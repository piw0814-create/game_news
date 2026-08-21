from types import SimpleNamespace

import app.service.article_recovery_service as recovery_module
from app.model.schemas import AnalysisStatus
from app.service.article_recovery_service import ArticleRecoveryService


def _candidate(article_id: int):
    return SimpleNamespace(id=article_id, analysisStatus=AnalysisStatus.FAILED)


def _configure(monkeypatch, *, batch=20, threshold=3):
    monkeypatch.setattr(recovery_module.settings, "analysis_recovery_limit", batch)
    monkeypatch.setattr(
        recovery_module.settings,
        "analysis_recovery_circuit_breaker_consecutive_failures",
        threshold,
    )
    monkeypatch.setattr(
        recovery_module.settings,
        "analysis_recovery_processing_stale_minutes",
        15,
    )
    monkeypatch.setattr(
        recovery_module.settings,
        "analysis_recovery_startup_retry_count",
        1,
    )


def test_recovery_processes_multiple_batches_without_retrying_same_article(monkeypatch):
    _configure(monkeypatch)
    service = ArticleRecoveryService()
    all_ids = list(range(1, 26))
    calls = []

    def fake_candidates(limit, processing_stale_minutes, pending_stale_minutes, exclude_ids):
        calls.append((limit, pending_stale_minutes, tuple(exclude_ids)))
        remaining = [article_id for article_id in all_ids if article_id not in set(exclude_ids)]
        return [_candidate(article_id) for article_id in remaining[:limit]]

    recovered = []
    monkeypatch.setattr(recovery_module.news_client, "get_recovery_candidates", fake_candidates)
    monkeypatch.setattr(
        service,
        "_recover_article",
        lambda article_id: recovered.append(article_id) or True,
    )

    service._run_recovery(
        mode="startup",
        max_total=25,
        pending_stale_minutes=0,
        query_retry=False,
    )

    assert recovered == all_ids
    assert len(set(recovered)) == 25
    assert [call[0] for call in calls] == [20, 5]
    assert calls[0][2] == ()
    assert set(calls[1][2]) == set(range(1, 21))


def test_recovery_opens_circuit_after_three_consecutive_failures(monkeypatch):
    _configure(monkeypatch, threshold=3)
    service = ArticleRecoveryService()
    attempted = []

    monkeypatch.setattr(
        recovery_module.news_client,
        "get_recovery_candidates",
        lambda **kwargs: [_candidate(article_id) for article_id in range(1, 11)],
    )
    monkeypatch.setattr(
        service,
        "_recover_article",
        lambda article_id: attempted.append(article_id) or False,
    )

    service._run_recovery(
        mode="periodic",
        max_total=100,
        pending_stale_minutes=15,
        query_retry=False,
    )

    assert attempted == [1, 2, 3]


def test_success_resets_consecutive_failure_counter(monkeypatch):
    _configure(monkeypatch, threshold=3)
    service = ArticleRecoveryService()
    attempted = []
    outcomes = {1: False, 2: False, 3: True, 4: False, 5: False, 6: False}

    monkeypatch.setattr(
        recovery_module.news_client,
        "get_recovery_candidates",
        lambda **kwargs: [_candidate(article_id) for article_id in range(1, 7)],
    )

    def recover(article_id):
        attempted.append(article_id)
        return outcomes[article_id]

    monkeypatch.setattr(service, "_recover_article", recover)

    service._run_recovery(
        mode="periodic",
        max_total=100,
        pending_stale_minutes=15,
        query_retry=False,
    )

    assert attempted == [1, 2, 3, 4, 5, 6]


def test_periodic_query_uses_pending_stale_window_and_excludes_attempted(monkeypatch):
    _configure(monkeypatch, batch=2)
    service = ArticleRecoveryService()
    captured = []

    def fake_candidates(limit, processing_stale_minutes, pending_stale_minutes, exclude_ids):
        captured.append(
            {
                "limit": limit,
                "processing": processing_stale_minutes,
                "pending": pending_stale_minutes,
                "exclude": tuple(exclude_ids),
            }
        )
        if not exclude_ids:
            return [_candidate(10), _candidate(11)]
        return []

    monkeypatch.setattr(recovery_module.news_client, "get_recovery_candidates", fake_candidates)
    monkeypatch.setattr(service, "_recover_article", lambda article_id: True)

    service._run_recovery(
        mode="periodic",
        max_total=10,
        pending_stale_minutes=15,
        query_retry=False,
    )

    assert captured[0]["pending"] == 15
    assert captured[0]["exclude"] == ()
    assert set(captured[1]["exclude"]) == {10, 11}


def test_startup_recovery_includes_fresh_pending_and_uses_max_total(monkeypatch):
    service = ArticleRecoveryService()
    monkeypatch.setattr(recovery_module.settings, "analysis_recovery_enabled", True)
    monkeypatch.setattr(recovery_module.settings, "analysis_recovery_max_total", 200)
    captured = {}

    def fake_run_recovery(**kwargs):
        captured.update(kwargs)

    monkeypatch.setattr(service, "_run_recovery", fake_run_recovery)
    service.recover()

    assert captured == {
        "mode": "startup",
        "max_total": 200,
        "pending_stale_minutes": 0,
        "query_retry": True,
    }
