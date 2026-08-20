from datetime import datetime, timezone

from app.client.openai_topic_analyzer import OpenAITopicAnalyzer
from app.model.schemas import (
    NewsCategory,
    TopicAnalysisArticleContext,
    TopicAnalysisContextResponse,
    TopicAnalysisFranchiseContext,
    TopicAnalysisTopicContext,
)


def test_topic_analysis_prompt_contains_franchise_context():
    context = TopicAnalysisContextResponse(
        topic=TopicAnalysisTopicContext(
            id=10,
            title="Mass Effect 프랜차이즈 차기작 논의",
            summary="차기작 관련 기사",
            category=NewsCategory.INDUSTRY,
        ),
        games=[],
        franchises=[
            TopicAnalysisFranchiseContext(
                id=2,
                name="Mass Effect",
                displayName=None,
                aliases=["ME"],
                isPrimary=True,
            )
        ],
        articles=[],
    )
    articles = [
        TopicAnalysisArticleContext(
            id=268,
            title="The next Mass Effect game remains uncertain",
            sourceName="PC Gamer",
            sourceType="MEDIA",
            collectedAt=datetime.now(timezone.utc),
            summary="정식 작품명이 특정되지 않은 차기 Mass Effect 게임을 다룬다.",
            category=NewsCategory.INDUSTRY,
        )
    ]

    prompt = OpenAITopicAnalyzer()._build_prompt(context, articles)

    assert "Related franchises:" in prompt
    assert "Canonical name: Mass Effect" in prompt
    assert "Aliases: ME" in prompt
    assert "Related games:\n(none)" in prompt
