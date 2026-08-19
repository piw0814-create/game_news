import logging
from datetime import datetime
from typing import List

from app.client.news_client import news_client
from app.client.openai_topic_analyzer import openai_topic_analyzer
from app.config.settings import settings
from app.model.schemas import (
    TopicAnalysisArticleContext,
    TopicAnalysisContextResponse,
    TopicReanalysisResponse,
)

logger = logging.getLogger(__name__)


class TopicAnalysisService:
    """Topic 전체 AI 분석과 객관적 중요도 보정을 담당한다."""

    OFFICIAL_BONUS = 8
    COMMUNITY_ONLY_PENALTY = 5

    def reanalyze(self, topic_id: int) -> TopicReanalysisResponse:
        context = news_client.get_topic_analysis_context(topic_id)
        if not context.articles:
            raise ValueError(f"Topic에 연결된 기사가 없습니다: {topic_id}")

        selected_articles = self._select_articles(context)
        logger.info(
            "[TopicAnalysis] 재분석 시작 - topicId=%s totalArticles=%s analyzedArticles=%s",
            topic_id,
            len(context.articles),
            len(selected_articles),
        )

        semantic = openai_topic_analyzer.analyze(context, selected_articles)
        official_bonus, source_bonus, community_penalty, final_score = self._score_importance(
            semantic.semanticImportanceScore,
            context.articles,
        )

        logger.info(
            "[TopicAnalysis] 중요도 계산 - topicId=%s semantic=%s officialBonus=%s "
            "sourceBonus=%s communityPenalty=%s final=%s",
            topic_id,
            semantic.semanticImportanceScore,
            official_bonus,
            source_bonus,
            community_penalty,
            final_score,
        )

        news_client.update_topic_analysis(
            topic_id=topic_id,
            title=semantic.title,
            summary=semantic.summary,
            category=semantic.category,
            importance_score=final_score,
            why_important=semantic.whyImportant,
        )

        logger.info(
            "[TopicAnalysis] 저장 완료 - topicId=%s importanceScore=%s",
            topic_id,
            final_score,
        )

        return TopicReanalysisResponse(
            topicId=topic_id,
            totalArticleCount=len(context.articles),
            analyzedArticleCount=len(selected_articles),
            semanticImportanceScore=semantic.semanticImportanceScore,
            officialBonus=official_bonus,
            sourceBonus=source_bonus,
            communityPenalty=community_penalty,
            importanceScore=final_score,
            title=semantic.title,
            summary=semantic.summary,
            category=semantic.category,
            whyImportant=semantic.whyImportant,
        )

    def _select_articles(
        self,
        context: TopicAnalysisContextResponse,
    ) -> List[TopicAnalysisArticleContext]:
        limit = max(1, settings.topic_analysis_article_limit)
        if len(context.articles) <= limit:
            return list(context.articles)

        def sort_key(article: TopicAnalysisArticleContext) -> tuple[int, float]:
            source_type = article.sourceType.strip().upper()
            reference_time: datetime = article.publishedAt or article.collectedAt
            return (1 if source_type == "OFFICIAL" else 0, reference_time.timestamp())

        return sorted(context.articles, key=sort_key, reverse=True)[:limit]

    def _score_importance(
        self,
        semantic_score: int,
        articles: List[TopicAnalysisArticleContext],
    ) -> tuple[int, int, int, int]:
        source_types = [article.sourceType.strip().upper() for article in articles]
        official_bonus = self.OFFICIAL_BONUS if "OFFICIAL" in source_types else 0

        unique_sources = {
            article.sourceName.strip().casefold()
            for article in articles
            if article.sourceName and article.sourceName.strip()
        }
        source_count = len(unique_sources)
        if source_count >= 4:
            source_bonus = 12
        elif source_count == 3:
            source_bonus = 8
        elif source_count == 2:
            source_bonus = 4
        else:
            source_bonus = 0

        community_penalty = (
            self.COMMUNITY_ONLY_PENALTY
            if source_types and all(source_type == "COMMUNITY" for source_type in source_types)
            else 0
        )

        final_score = semantic_score + official_bonus + source_bonus - community_penalty
        final_score = max(0, min(100, final_score))

        return official_bonus, source_bonus, community_penalty, final_score


topic_analysis_service = TopicAnalysisService()
