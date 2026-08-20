import sys
import types
from datetime import datetime, timezone
from types import SimpleNamespace

from pydantic import ValidationError

openai_stub = types.ModuleType("openai")
openai_stub.OpenAI = object
sys.modules.setdefault("openai", openai_stub)

from app.client.openai_client import OpenAIArticleAnalyzer
from app.model.schemas import (
    AnalysisStatus,
    ArticleAnalysisResult,
    NewsArticleResponse,
)


def _validation_error() -> ValidationError:
    try:
        ArticleAnalysisResult.model_validate_json('{"entityType":"MIXED","gameNewsRelevant":true,"summary":"잘린')
    except ValidationError as exc:
        return exc
    raise AssertionError("ValidationError expected")


def _article() -> NewsArticleResponse:
    return NewsArticleResponse(
        id=7,
        title="Retry structured output",
        url="https://example.com/retry-structured-output",
        sourceName="Test",
        sourceType="MEDIA",
        collectedAt=datetime.now(timezone.utc),
        content="A test article about a game.",
        analysisStatus=AnalysisStatus.PROCESSING,
    )


def _result() -> ArticleAnalysisResult:
    return ArticleAnalysisResult.model_validate(
        {
            "entityType": "SPECIFIC_GAME",
            "gameNewsRelevant": True,
            "summary": "테스트 기사 요약입니다.",
            "category": "UPDATE",
            "keywords": ["테스트", "업데이트", "게임"],
            "relatedGames": [],
            "relatedFranchises": [],
        }
    )


class FakeResponses:
    def __init__(self, outcomes):
        self.outcomes = list(outcomes)
        self.calls = 0

    def parse(self, **kwargs):
        self.calls += 1
        outcome = self.outcomes.pop(0)
        if isinstance(outcome, Exception):
            raise outcome
        return SimpleNamespace(output_parsed=outcome)


class FakeClient:
    def __init__(self, outcomes):
        self.responses = FakeResponses(outcomes)


def test_article_analysis_retries_once_after_validation_error():
    analyzer = OpenAIArticleAnalyzer()
    fake_client = FakeClient([_validation_error(), _result()])
    analyzer._client = fake_client
    analyzer._get_client = lambda: fake_client

    result = analyzer.analyze(_article(), [], [])

    assert result.category.value == "UPDATE"
    assert fake_client.responses.calls == 2


def test_article_analysis_raises_after_second_validation_error():
    analyzer = OpenAIArticleAnalyzer()
    fake_client = FakeClient([_validation_error(), _validation_error()])
    analyzer._client = fake_client
    analyzer._get_client = lambda: fake_client

    try:
        analyzer.analyze(_article(), [], [])
    except ValidationError:
        pass
    else:
        raise AssertionError("second ValidationError must be raised")

    assert fake_client.responses.calls == 2
