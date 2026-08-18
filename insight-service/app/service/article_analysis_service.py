import logging

from app.client.news_client import NewsServiceError, news_client
from app.client.openai_client import openai_article_analyzer
from app.model.schemas import AnalysisStatus
from app.service.topic_analysis_service import topic_analysis_service
from app.service.topic_integration_service import topic_integration_service

logger = logging.getLogger(__name__)


class ArticleAnalysisService:
    """news.created 한 건의 AI 기사 분석 전체 흐름을 담당한다."""

    def process(self, article_id: int) -> bool:
        """
        Kafka offset commit 가능 여부를 반환한다.
        - True: 정상 완료, 이미 완료, 영구적으로 무시할 이벤트, 또는 FAILED 상태 기록 완료
        - False: News Service 장애 등으로 상태 기록조차 못해 재처리가 필요
        """
        try:
            article = news_client.get_news(article_id)
        except NewsServiceError as exc:
            if exc.status_code == 404:
                logger.warning(
                    "[ArticleAnalysis] 존재하지 않는 기사 이벤트 무시 - articleId=%s",
                    article_id,
                )
                return True
            logger.error(
                "[ArticleAnalysis] 기사 조회 실패 - articleId=%s error=%s",
                article_id,
                exc,
            )
            return False

        if article.analysisStatus == AnalysisStatus.COMPLETED:
            logger.info(
                "[ArticleAnalysis] 이미 분석 완료된 기사 skip - articleId=%s",
                article_id,
            )
            return True

        try:
            news_client.update_analysis_status(article_id, AnalysisStatus.PROCESSING)
            logger.info(
                "[ArticleAnalysis] 상태 변경 - articleId=%s status=PROCESSING",
                article_id,
            )

            games = news_client.get_games()
            analysis = openai_article_analyzer.analyze(article, games)

            if not analysis.gameNewsRelevant:
                completed = news_client.update_analysis(
                    article_id=article_id,
                    summary=analysis.summary,
                    category=analysis.category,
                    keywords=analysis.keywords,
                )
                logger.info(
                    "[ArticleAnalysis] 게임/IP 생태계 비관련 - Topic 생성 생략 "
                    "articleId=%s status=%s",
                    article_id,
                    completed.analysisStatus.value,
                )
                return True

            self._link_existing_games(article_id, analysis, games)

            topic_result = topic_integration_service.integrate(article, analysis)
            logger.info(
                "[ArticleAnalysis] Topic 통합 완료 - articleId=%s topicId=%s action=%s",
                article_id,
                topic_result.topicId,
                topic_result.action.value,
            )

            completed = news_client.update_analysis(
                article_id=article_id,
                summary=analysis.summary,
                category=analysis.category,
                keywords=analysis.keywords,
            )

            logger.info(
                "[ArticleAnalysis] 분석 저장 완료 - articleId=%s status=%s",
                article_id,
                completed.analysisStatus.value,
            )

            try:
                topic_analysis = topic_analysis_service.reanalyze(topic_result.topicId)
                logger.info(
                    "[ArticleAnalysis] Topic 재분석 완료 - articleId=%s topicId=%s importanceScore=%s",
                    article_id,
                    topic_result.topicId,
                    topic_analysis.importanceScore,
                )
            except Exception as exc:
                # Topic 파생 분석 실패는 이미 완료된 기사/Topic 관계를 롤백하지 않는다.
                logger.exception(
                    "[ArticleAnalysis] Topic 재분석 실패 - articleId=%s topicId=%s "
                    "기존 Topic 분석 정보 유지 error=%s",
                    article_id,
                    topic_result.topicId,
                    exc,
                )

            return True

        except Exception as exc:
            logger.exception(
                "[ArticleAnalysis] 분석 실패 - articleId=%s error=%s",
                article_id,
                exc,
            )
            return self._mark_failed(article_id)

    def _link_existing_games(self, article_id: int, analysis, games) -> None:
        game_by_name = {game.name.strip().casefold(): game for game in games}
        existing_links = news_client.get_article_games(article_id)
        existing_game_ids = {link.gameId for link in existing_links}

        for related_game in analysis.relatedGames:
            matched = game_by_name.get(related_game.name.strip().casefold())
            if matched is None:
                logger.info(
                    "[ArticleAnalysis] 미등록 게임 자동 생성 안 함 - articleId=%s game=%s",
                    article_id,
                    related_game.name,
                )
                continue

            if matched.id in existing_game_ids:
                logger.info(
                    "[ArticleAnalysis] 이미 연결된 게임 skip - articleId=%s gameId=%s",
                    article_id,
                    matched.id,
                )
                continue

            news_client.link_game(
                article_id=article_id,
                game_id=matched.id,
                is_primary=related_game.isPrimary,
                confidence_score=related_game.confidenceScore,
            )
            existing_game_ids.add(matched.id)
            logger.info(
                "[ArticleAnalysis] ArticleGame 연결 - articleId=%s gameId=%s confidence=%.2f",
                article_id,
                matched.id,
                related_game.confidenceScore,
            )

    def _mark_failed(self, article_id: int) -> bool:
        try:
            news_client.update_analysis_status(article_id, AnalysisStatus.FAILED)
            logger.info(
                "[ArticleAnalysis] 상태 변경 - articleId=%s status=FAILED",
                article_id,
            )
            return True
        except Exception as exc:
            logger.error(
                "[ArticleAnalysis] FAILED 상태 기록 실패 - articleId=%s error=%s",
                article_id,
                exc,
            )
            return False


article_analysis_service = ArticleAnalysisService()
