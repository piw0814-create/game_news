import logging
from typing import List

from openai import OpenAI

from app.config.settings import settings
from app.client.openai_usage import log_openai_usage
from app.model.schemas import (
    ArticleAnalysisResult,
    NewsArticleResponse,
    TopicCandidateResponse,
    TopicMatchResult,
)

logger = logging.getLogger(__name__)


class OpenAITopicMatcher:
    """이미 좁혀진 Topic 후보만 대상으로 동일 사건 여부를 최종 판단한다."""

    def __init__(self):
        self._client: OpenAI | None = None

    def match(
        self,
        article: NewsArticleResponse,
        analysis: ArticleAnalysisResult,
        candidates: List[TopicCandidateResponse],
    ) -> TopicMatchResult:
        if not candidates:
            raise ValueError("Topic 후보가 없으면 AI 동일 사건 판단을 호출하지 않습니다")

        client = self._get_client()
        prompt = self._build_prompt(article, analysis, candidates)

        logger.info(
            "[OpenAITopicMatcher] 동일 사건 판단 시작 - articleId=%s candidates=%s model=%s",
            article.id,
            len(candidates),
            settings.openai_model,
        )

        response = client.responses.parse(
            model=settings.openai_model,
            input=[
                {
                    "role": "system",
                    "content": (
                        "You decide whether one game-news article describes the same real-world event "
                        "as one of the supplied Topic candidates. Return only the requested structured result. "
                        "Same game, company, or category alone is not enough. The concrete event must match. "
                        "Prefer no match when uncertain. Never choose a Topic ID outside the supplied candidates."
                    ),
                },
                {"role": "user", "content": prompt},
            ],
            text_format=TopicMatchResult,
            max_output_tokens=settings.openai_topic_match_max_output_tokens,
            prompt_cache_key="game-intelligence:topic-match:v1",
        )
        log_openai_usage(response, "topic_match", article.id)

        result = response.output_parsed
        if result is None:
            output_text = (getattr(response, "output_text", "") or "").strip()
            logger.error(
                "[OpenAITopicMatcher] Structured Output 파싱 실패 - articleId=%s responseId=%s status=%s incompleteDetails=%s outputText=%r",
                article.id,
                getattr(response, "id", None),
                getattr(response, "status", None),
                getattr(response, "incomplete_details", None),
                output_text[:500],
            )
            raise RuntimeError("OpenAI Topic Structured Output을 파싱하지 못했습니다")

        logger.info(
            "[OpenAITopicMatcher] 판단 완료 - articleId=%s sameEvent=%s topicId=%s confidence=%.2f",
            article.id,
            result.sameEvent,
            result.matchedTopicId,
            result.confidenceScore,
        )
        return result

    def _get_client(self) -> OpenAI:
        if not settings.openai_api_key.strip():
            raise RuntimeError("OPENAI_API_KEY가 설정되지 않았습니다")

        if self._client is None:
            self._client = OpenAI(api_key=settings.openai_api_key)
        return self._client

    def _build_prompt(
        self,
        article: NewsArticleResponse,
        analysis: ArticleAnalysisResult,
        candidates: List[TopicCandidateResponse],
    ) -> str:
        candidate_blocks = []
        for candidate in candidates:
            summary = (candidate.summary or "").strip()[:1500]
            candidate_blocks.append(
                "\n".join(
                    [
                        f"Topic ID: {candidate.id}",
                        f"Title: {candidate.title}",
                        f"Summary: {summary or '(none)'}",
                        f"Category: {candidate.category.value if candidate.category else 'unknown'}",
                        f"First seen: {candidate.firstSeenAt}",
                        f"Last updated: {candidate.lastUpdatedAt}",
                    ]
                )
            )

        candidate_text = "\n\n---\n\n".join(candidate_blocks)
        keyword_text = ", ".join(analysis.keywords)

        return f"""
Compare the new article with the Topic candidates below.

Decision rules:
- sameEvent=true only when the article and one candidate refer to the same concrete announcement, release change, update, incident, event, controversy, or industry action.
- Sharing the same game is not enough.
- A general follow-up, separate patch, separate rumor, or different announcement must be treated as a different event.
- If multiple candidates look similar, choose only the single best match.
- If evidence is weak or ambiguous, return sameEvent=false and matchedTopicId=null.
- confidenceScore must reflect confidence in the final decision.
- reason should be concise.

New article:
Title: {article.title}
Summary: {analysis.summary}
Category: {analysis.category.value}
Keywords: {keyword_text}
Published at: {article.publishedAt or article.collectedAt}

Topic candidates:
{candidate_text}
""".strip()


openai_topic_matcher = OpenAITopicMatcher()
