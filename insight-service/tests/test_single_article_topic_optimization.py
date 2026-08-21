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
from app.service import topic_integration_service as integration_module


def _article(source_type: str = "MEDIA") -> NewsArticleResponse:
    return NewsArticleResponse(
        id=700,
        title="Test article headline",
        url="https://example.com/test",
        sourceName="Test Source",
        sourceType=source_type,
        collectedAt=datetime.now(timezone.utc),
        content="body",
        analysisStatus=AnalysisStatus.PROCESSING,
    )


def _analysis(with_initial: bool = True) -> ArticleAnalysisResult:
    return ArticleAnalysisResult(
        entityType=ArticleEntityType.NONE,
        gameNewsRelevant=True,
        summary="기사 요약",
        category=NewsCategory.INDUSTRY,
        topicTitle="초기 토픽 제목" if with_initial else None,
        semanticImportanceScore=31 if with_initial else None,
        whyImportant="실질적인 영향 설명" if with_initial else None,
        keywords=["industry", "test", "news"],
        relatedGames=[],
        relatedFranchises=[],
    )


def test_new_topic_receives_initial_article_ai_analysis(monkeypatch):
    captured = {}

    monkeypatch.setattr(integration_module.news_client, "get_topic_candidates", lambda **kwargs: [])

    def fake_integrate_topic(**kwargs):
        captured.update(kwargs)
        return TopicIntegrationResponse(topicId=701, action=TopicIntegrationAction.CREATED_NEW)

    monkeypatch.setattr(integration_module.news_client, "integrate_topic", fake_integrate_topic)

    result = integration_module.topic_integration_service.integrate(
        _article(),
        _analysis(),
        initial_importance_score=31,
        initial_why_important="실질적인 영향 설명",
    )

    assert result.action == TopicIntegrationAction.CREATED_NEW
    assert captured["title"] == "초기 토픽 제목"
    assert captured["summary"] == "기사 요약"
    assert captured["importance_score"] == 31
    assert captured["why_important"] == "실질적인 영향 설명"


def test_missing_initial_topic_fields_falls_back_to_article_title(monkeypatch):
    captured = {}
    monkeypatch.setattr(integration_module.news_client, "get_topic_candidates", lambda **kwargs: [])

    def fake_integrate_topic(**kwargs):
        captured.update(kwargs)
        return TopicIntegrationResponse(topicId=702, action=TopicIntegrationAction.CREATED_NEW)

    monkeypatch.setattr(integration_module.news_client, "integrate_topic", fake_integrate_topic)
    integration_module.topic_integration_service.integrate(_article(), _analysis(False))

    assert captured["title"] == "Test article headline"
    assert captured["importance_score"] is None
    assert captured["why_important"] is None


def test_article_service_only_skips_topic_analyzer_for_complete_new_topic():
    service = ArticleAnalysisService()
    complete = _analysis(True)
    incomplete = _analysis(False)

    assert service._has_initial_topic_analysis(complete) is True
    assert service._has_initial_topic_analysis(incomplete) is False
    assert service._should_reanalyze_topic(TopicIntegrationAction.CREATED_NEW, True) is False
    assert service._should_reanalyze_topic(TopicIntegrationAction.CREATED_NEW, False) is True
    assert service._should_reanalyze_topic(TopicIntegrationAction.LINKED_EXISTING, True) is True
    assert service._should_reanalyze_topic(TopicIntegrationAction.ALREADY_LINKED, True) is True


def test_initial_importance_uses_same_source_adjustments():
    from app.service.topic_analysis_service import TopicAnalysisService

    service = TopicAnalysisService()
    assert service.score_initial_importance(30, "Official Blog", "OFFICIAL") == (8, 0, 0, 38)
    assert service.score_initial_importance(30, "Forum", "COMMUNITY") == (0, 0, 5, 25)
    assert service.score_initial_importance(30, "Media", "MEDIA") == (0, 0, 0, 30)


def test_process_skips_topic_analyzer_for_new_topic_with_complete_initial_analysis(monkeypatch):
    from app.service import article_analysis_service as article_module

    article = _article()
    analysis = _analysis(True)
    calls = {"reanalyze": 0, "integrate": 0}

    fake_news = SimpleNamespace(
        get_news=lambda article_id: article,
        update_analysis_status=lambda article_id, status: None,
        update_analysis=lambda **kwargs: SimpleNamespace(analysisStatus=AnalysisStatus.COMPLETED),
    )
    monkeypatch.setattr(article_module, "news_client", fake_news)
    monkeypatch.setattr(article_module.openai_article_analyzer, "analyze", lambda value: analysis)
    monkeypatch.setattr(
        article_module.topic_analysis_service,
        "score_initial_importance",
        lambda *args: (0, 0, 0, 31),
    )

    def fake_integrate(*args, **kwargs):
        calls["integrate"] += 1
        assert kwargs["initial_importance_score"] == 31
        assert kwargs["initial_why_important"] == "실질적인 영향 설명"
        return TopicIntegrationResponse(topicId=800, action=TopicIntegrationAction.CREATED_NEW)

    def fail_reanalyze(topic_id):
        calls["reanalyze"] += 1
        raise AssertionError("새 단일 기사 Topic은 Topic Analyzer를 호출하면 안 됩니다")

    monkeypatch.setattr(article_module.topic_integration_service, "integrate", fake_integrate)
    monkeypatch.setattr(article_module.topic_analysis_service, "reanalyze", fail_reanalyze)

    assert ArticleAnalysisService().process(article.id) is True
    assert calls == {"reanalyze": 0, "integrate": 1}


def test_process_falls_back_to_topic_analyzer_when_initial_fields_are_missing(monkeypatch):
    from app.service import article_analysis_service as article_module

    article = _article()
    analysis = _analysis(False)
    calls = {"reanalyze": 0}

    fake_news = SimpleNamespace(
        get_news=lambda article_id: article,
        update_analysis_status=lambda article_id, status: None,
        update_analysis=lambda **kwargs: SimpleNamespace(analysisStatus=AnalysisStatus.COMPLETED),
    )
    monkeypatch.setattr(article_module, "news_client", fake_news)
    monkeypatch.setattr(article_module.openai_article_analyzer, "analyze", lambda value: analysis)
    monkeypatch.setattr(
        article_module.topic_integration_service,
        "integrate",
        lambda *args, **kwargs: TopicIntegrationResponse(
            topicId=801,
            action=TopicIntegrationAction.CREATED_NEW,
        ),
    )

    def fake_reanalyze(topic_id):
        calls["reanalyze"] += 1
        return SimpleNamespace(importanceScore=27)

    monkeypatch.setattr(article_module.topic_analysis_service, "reanalyze", fake_reanalyze)

    assert ArticleAnalysisService().process(article.id) is True
    assert calls["reanalyze"] == 1
