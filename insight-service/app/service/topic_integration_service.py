import logging
import re
from datetime import datetime
from typing import List

from app.client.news_client import news_client
from app.client.openai_topic_matcher import openai_topic_matcher
from app.config.settings import settings
from app.model.schemas import (
    ArticleAnalysisResult,
    NewsArticleResponse,
    TopicCandidateResponse,
    TopicIntegrationResponse,
)

logger = logging.getLogger(__name__)


class TopicIntegrationService:
    """코드로 후보를 줄이고 AI로 최종 사건 동일성을 판단한 뒤 Topic에 통합한다."""

    def integrate(
        self,
        article: NewsArticleResponse,
        analysis: ArticleAnalysisResult,
    ) -> TopicIntegrationResponse:
        candidates = news_client.get_topic_candidates(
            article_id=article.id,
            window_hours=settings.topic_candidate_window_hours,
            limit=settings.topic_candidate_db_limit,
        )

        logger.info(
            "[TopicIntegration] 후보 조회 - articleId=%s window=%sh dbCandidates=%s",
            article.id,
            settings.topic_candidate_window_hours,
            len(candidates),
        )

        if not candidates:
            return self._create_new_topic(article, analysis, "후보 없음")

        ranked = self._rank_candidates(article, analysis, candidates)
        ai_candidates = ranked[: settings.topic_candidate_ai_limit]

        logger.info(
            "[TopicIntegration] AI 비교 후보 축소 - articleId=%s aiCandidates=%s topicIds=%s",
            article.id,
            len(ai_candidates),
            [candidate.id for candidate in ai_candidates],
        )

        try:
            match = openai_topic_matcher.match(article, analysis, ai_candidates)
        except Exception as exc:
            logger.warning(
                "[TopicIntegration] AI matcher 실패 - 새 Topic fallback - articleId=%s error=%s",
                article.id,
                exc,
            )
            return self._create_new_topic(
                article,
                analysis,
                f"AI matcher failure fallback - {type(exc).__name__}",
            )

        candidate_ids = {candidate.id for candidate in ai_candidates}

        if (
            match.sameEvent
            and match.matchedTopicId in candidate_ids
            and match.confidenceScore >= settings.topic_match_confidence_threshold
        ):
            result = news_client.integrate_topic(
                article_id=article.id,
                target_topic_id=match.matchedTopicId,
                title=article.title,
                summary=analysis.summary,
                category=analysis.category,
            )
            logger.info(
                "[TopicIntegration] 기존 Topic 연결 - articleId=%s topicId=%s confidence=%.2f action=%s",
                article.id,
                result.topicId,
                match.confidenceScore,
                result.action.value,
            )
            return result

        reason = (
            f"AI no-match/threshold - sameEvent={match.sameEvent} "
            f"topicId={match.matchedTopicId} confidence={match.confidenceScore:.2f}"
        )
        return self._create_new_topic(article, analysis, reason)

    def _create_new_topic(
        self,
        article: NewsArticleResponse,
        analysis: ArticleAnalysisResult,
        reason: str,
    ) -> TopicIntegrationResponse:
        result = news_client.integrate_topic(
            article_id=article.id,
            target_topic_id=None,
            title=article.title,
            summary=analysis.summary,
            category=analysis.category,
        )
        logger.info(
            "[TopicIntegration] 새 Topic 처리 - articleId=%s topicId=%s action=%s reason=%s",
            article.id,
            result.topicId,
            result.action.value,
            reason,
        )
        return result

    def _rank_candidates(
        self,
        article: NewsArticleResponse,
        analysis: ArticleAnalysisResult,
        candidates: List[TopicCandidateResponse],
    ) -> List[TopicCandidateResponse]:
        reference_time = article.publishedAt or article.collectedAt

        def score(candidate: TopicCandidateResponse) -> tuple[float, datetime]:
            value = 0.0
            if candidate.category == analysis.category:
                value += 2.0

            searchable = self._normalize_text(
                f"{candidate.title} {candidate.summary or ''}"
            )
            for keyword in analysis.keywords:
                normalized_keyword = self._normalize_text(keyword)
                if normalized_keyword and normalized_keyword in searchable:
                    value += 1.0

            hours = abs((candidate.lastUpdatedAt - reference_time).total_seconds()) / 3600
            if hours <= 6:
                value += 2.0
            elif hours <= 24:
                value += 1.0
            elif hours <= settings.topic_candidate_window_hours:
                value += 0.5

            return value, candidate.lastUpdatedAt

        return sorted(candidates, key=score, reverse=True)

    def _normalize_text(self, value: str) -> str:
        return re.sub(r"[^0-9a-z가-힣]+", " ", value.casefold()).strip()


topic_integration_service = TopicIntegrationService()
