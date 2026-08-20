import sys
import types
from datetime import datetime, timezone

openai_stub = types.ModuleType("openai")
openai_stub.OpenAI = object
sys.modules.setdefault("openai", openai_stub)

from app.client.openai_client import OpenAIArticleAnalyzer
from app.model.schemas import AnalysisStatus, NewsArticleResponse


def test_unknown_article_entity_can_be_returned_for_backend_igdb_verification():
    article = NewsArticleResponse(
        id=999,
        title="A newly announced franchise gets its first game",
        url="https://example.com/entity-review",
        sourceName="Test",
        sourceType="MEDIA",
        collectedAt=datetime.now(timezone.utc),
        content="The article explicitly names a new game franchise.",
        analysisStatus=AnalysisStatus.PROCESSING,
    )

    prompt = OpenAIArticleAnalyzer()._build_prompt(article, [], [])

    assert "Known franchises are identity hints, not a whitelist" in prompt
    assert "backend will verify the returned name against IGDB" in prompt
    assert "route ambiguous matches to admin review" in prompt
