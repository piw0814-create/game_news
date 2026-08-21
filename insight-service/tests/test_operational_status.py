from app.config.settings import settings
from app.kafka.consumer import NewsCreatedConsumer
from app.service.article_recovery_service import ArticleRecoveryService


class _AliveThread:
    def is_alive(self):
        return True


def test_consumer_operational_status_exposes_runtime_state():
    consumer = NewsCreatedConsumer()
    consumer._thread = _AliveThread()
    consumer._connected = True
    consumer._last_consumed_at = "2026-08-21T06:00:00+00:00"
    consumer._last_commit_at = "2026-08-21T06:00:01+00:00"
    consumer._last_error_at = "2026-08-21T05:59:00+00:00"
    consumer._last_error = "temporary kafka error"

    status = consumer.get_operational_status()

    assert status == {
        "alive": True,
        "connected": True,
        "lastConsumedAt": "2026-08-21T06:00:00+00:00",
        "lastCommitAt": "2026-08-21T06:00:01+00:00",
        "lastErrorAt": "2026-08-21T05:59:00+00:00",
        "lastError": "temporary kafka error",
    }


def test_recovery_operational_status_exposes_periodic_thread(monkeypatch):
    recovery = ArticleRecoveryService()
    recovery._periodic_thread = _AliveThread()

    monkeypatch.setattr(settings, "analysis_recovery_enabled", True)
    monkeypatch.setattr(settings, "analysis_recovery_periodic_enabled", True)

    status = recovery.get_operational_status()

    assert status["alive"] is True
    assert status["enabled"] is True
    assert status["periodicEnabled"] is True
