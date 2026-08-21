from datetime import datetime, timezone

from app.model.schemas import (
    AnalysisStatus,
    ArticleAnalysisResult,
    ArticleEntityType,
    NewsArticleResponse,
    NewsCategory,
    RelatedGameAnalysis,
    TopicIntegrationAction,
    TopicIntegrationResponse,
)
from app.service import topic_integration_service as module


def _article() -> NewsArticleResponse:
    return NewsArticleResponse(
        id=500,
        title="The Finals patch notes",
        url="https://example.com/the-finals",
        sourceName="Test",
        sourceType="MEDIA",
        collectedAt=datetime.now(timezone.utc),
        content="The Finals received a patch.",
        analysisStatus=AnalysisStatus.PROCESSING,
    )


def _analysis(entity_type: ArticleEntityType) -> ArticleAnalysisResult:
    games = []
    if entity_type == ArticleEntityType.SPECIFIC_GAME:
        games = [
            RelatedGameAnalysis(
                name="The Finals",
                entityType=ArticleEntityType.SPECIFIC_GAME,
                isPrimary=True,
                confidenceScore=0.99,
                reason="기사에서 직접 다룬다.",
            )
        ]
    return ArticleAnalysisResult(
        entityType=entity_type,
        gameNewsRelevant=True,
        summary="테스트 요약입니다.",
        category=NewsCategory.UPDATE,
        keywords=["The Finals", "patch", "update"],
        relatedGames=games,
        relatedFranchises=[],
    )


def test_specific_game_disables_recent_topic_fallback(monkeypatch):
    captured = {}

    def fake_candidates(**kwargs):
        captured.update(kwargs)
        return []

    monkeypatch.setattr(module.news_client, "get_topic_candidates", fake_candidates)
    monkeypatch.setattr(
        module.news_client,
        "integrate_topic",
        lambda **kwargs: TopicIntegrationResponse(
            topicId=900,
            action=TopicIntegrationAction.CREATED_NEW,
        ),
    )
    matcher_called = {"value": False}

    def fail_if_matcher_called(*args, **kwargs):
        matcher_called["value"] = True
        raise AssertionError("entity candidate가 없으면 matcher를 호출하면 안 됩니다")

    monkeypatch.setattr(module.openai_topic_matcher, "match", fail_if_matcher_called)

    result = module.topic_integration_service.integrate(
        _article(),
        _analysis(ArticleEntityType.SPECIFIC_GAME),
    )

    assert captured["allow_recent_fallback"] is False
    assert matcher_called["value"] is False
    assert result.action == TopicIntegrationAction.CREATED_NEW


def test_none_entity_keeps_recent_topic_fallback(monkeypatch):
    captured = {}

    def fake_candidates(**kwargs):
        captured.update(kwargs)
        return []

    monkeypatch.setattr(module.news_client, "get_topic_candidates", fake_candidates)
    monkeypatch.setattr(
        module.news_client,
        "integrate_topic",
        lambda **kwargs: TopicIntegrationResponse(
            topicId=901,
            action=TopicIntegrationAction.CREATED_NEW,
        ),
    )

    module.topic_integration_service.integrate(
        _article(),
        _analysis(ArticleEntityType.NONE),
    )

    assert captured["allow_recent_fallback"] is True
