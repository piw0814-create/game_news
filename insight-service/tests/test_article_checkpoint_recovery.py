import sys
import types

openai_stub = types.ModuleType("openai")
openai_stub.OpenAI = object
sys.modules.setdefault("openai", openai_stub)

from datetime import datetime, timezone
from types import SimpleNamespace

from app.model.schemas import (
    AnalysisStatus,
    ArticleAnalysisResult,
    ArticleEntityType,
    NewsArticleResponse,
    NewsCategory,
    TopicIntegrationAction,
    TopicIntegrationResponse,
)
from app.service.article_analysis_service import ArticleAnalysisService


def _article(status: AnalysisStatus) -> NewsArticleResponse:
    return NewsArticleResponse(
        id=910,
        title="Checkpoint recovery article",
        url="https://example.com/checkpoint-recovery",
        sourceName="Checkpoint Source",
        sourceType="MEDIA",
        collectedAt=datetime.now(timezone.utc),
        content="body",
        analysisStatus=status,
    )


def _analysis() -> ArticleAnalysisResult:
    return ArticleAnalysisResult(
        entityType=ArticleEntityType.NONE,
        gameNewsRelevant=True,
        summary="체크포인트 기사 요약",
        category=NewsCategory.INDUSTRY,
        topicTitle="체크포인트 Topic",
        semanticImportanceScore=28,
        whyImportant="재호출 없이 복구해야 한다.",
        keywords=["checkpoint", "recovery", "topic"],
        relatedGames=[],
        relatedFranchises=[],
    )


def test_topic_pending_resume_skips_article_analyzer_and_entity_resolution(monkeypatch):
    from app.service import article_analysis_service as module

    article = _article(AnalysisStatus.TOPIC_PENDING)
    analysis = _analysis()
    status_updates = []

    fake_news = SimpleNamespace(
        get_news=lambda article_id: article,
        get_analysis_checkpoint=lambda article_id: analysis,
        update_analysis_status=lambda article_id, status: (
            status_updates.append(status)
            or article.model_copy(update={"analysisStatus": status})
        ),
    )
    monkeypatch.setattr(module, "news_client", fake_news)
    monkeypatch.setattr(
        module.openai_article_analyzer,
        "analyze",
        lambda value: (_ for _ in ()).throw(AssertionError("Article Analyzer 재호출 금지")),
    )
    monkeypatch.setattr(
        module.topic_analysis_service,
        "score_initial_importance",
        lambda *args: (0, 0, 0, 28),
    )
    monkeypatch.setattr(
        module.topic_integration_service,
        "integrate",
        lambda *args, **kwargs: TopicIntegrationResponse(
            topicId=911,
            action=TopicIntegrationAction.CREATED_NEW,
        ),
    )
    monkeypatch.setattr(
        module.topic_analysis_service,
        "reanalyze",
        lambda topic_id: (_ for _ in ()).throw(AssertionError("Topic Analyzer 호출 금지")),
    )

    service = ArticleAnalysisService()
    monkeypatch.setattr(
        service,
        "_link_games",
        lambda *args, **kwargs: (_ for _ in ()).throw(AssertionError("Entity Resolution 재실행 금지")),
    )
    monkeypatch.setattr(
        service,
        "_link_franchises",
        lambda *args, **kwargs: (_ for _ in ()).throw(AssertionError("Entity Resolution 재실행 금지")),
    )

    assert service.process(article.id) is True
    assert status_updates == [AnalysisStatus.COMPLETED]


def test_analyzed_resume_skips_article_analyzer_and_continues_entity_resolution(monkeypatch):
    from app.service import article_analysis_service as module

    article = _article(AnalysisStatus.ANALYZED)
    analysis = _analysis()
    status_updates = []
    calls = {"games": 0, "franchises": 0}

    fake_news = SimpleNamespace(
        get_news=lambda article_id: article,
        get_analysis_checkpoint=lambda article_id: analysis,
        update_analysis_status=lambda article_id, status: (
            status_updates.append(status)
            or article.model_copy(update={"analysisStatus": status})
        ),
    )
    monkeypatch.setattr(module, "news_client", fake_news)
    monkeypatch.setattr(
        module.openai_article_analyzer,
        "analyze",
        lambda value: (_ for _ in ()).throw(AssertionError("Article Analyzer 재호출 금지")),
    )
    monkeypatch.setattr(
        module.topic_analysis_service,
        "score_initial_importance",
        lambda *args: (0, 0, 0, 28),
    )
    monkeypatch.setattr(
        module.topic_integration_service,
        "integrate",
        lambda *args, **kwargs: TopicIntegrationResponse(
            topicId=912,
            action=TopicIntegrationAction.CREATED_NEW,
        ),
    )
    monkeypatch.setattr(
        module.topic_analysis_service,
        "reanalyze",
        lambda topic_id: (_ for _ in ()).throw(AssertionError("Topic Analyzer 호출 금지")),
    )

    service = ArticleAnalysisService()
    monkeypatch.setattr(service, "_link_games", lambda *args: calls.__setitem__("games", calls["games"] + 1))
    monkeypatch.setattr(service, "_link_franchises", lambda *args: calls.__setitem__("franchises", calls["franchises"] + 1))

    assert service.process(article.id) is True
    assert calls == {"games": 1, "franchises": 1}
    assert status_updates == [AnalysisStatus.TOPIC_PENDING, AnalysisStatus.COMPLETED]


def test_topic_pending_failure_keeps_checkpoint_without_article_ai_recall(monkeypatch):
    from app.service import article_analysis_service as module

    article = _article(AnalysisStatus.TOPIC_PENDING)
    analysis = _analysis()
    status_updates = []

    fake_news = SimpleNamespace(
        get_news=lambda article_id: article,
        get_analysis_checkpoint=lambda article_id: analysis,
        update_analysis_status=lambda article_id, status: status_updates.append(status),
    )
    monkeypatch.setattr(module, "news_client", fake_news)
    monkeypatch.setattr(
        module.openai_article_analyzer,
        "analyze",
        lambda value: (_ for _ in ()).throw(AssertionError("Article Analyzer 재호출 금지")),
    )
    monkeypatch.setattr(
        module.topic_analysis_service,
        "score_initial_importance",
        lambda *args: (0, 0, 0, 28),
    )
    monkeypatch.setattr(
        module.topic_integration_service,
        "integrate",
        lambda *args, **kwargs: (_ for _ in ()).throw(RuntimeError("matcher temporary failure")),
    )

    assert ArticleAnalysisService().process(article.id) is True
    assert status_updates == []
