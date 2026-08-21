import logging

from app.client.news_client import NewsServiceError, news_client
from app.client.openai_client import openai_article_analyzer
from app.model.schemas import (
    AnalysisStatus,
    ArticleEntityType,
    EntityResolutionOutcome,
    TopicIntegrationAction,
)
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

            analysis = openai_article_analyzer.analyze(article)

            if not analysis.gameNewsRelevant:
                completed = news_client.update_analysis(
                    article_id=article_id,
                    summary=analysis.summary,
                    category=analysis.category,
                    keywords=analysis.keywords,
                    game_news_relevant=analysis.gameNewsRelevant,
                    entity_type=analysis.entityType,
                )
                logger.info(
                    "[ArticleAnalysis] 게임/IP 생태계 비관련 - Topic 생성 생략 "
                    "articleId=%s status=%s",
                    article_id,
                    completed.analysisStatus.value,
                )
                return True

            logger.info(
                "[ArticleAnalysis] 엔티티 범위 판정 - articleId=%s entityType=%s",
                article_id,
                getattr(analysis.entityType, "value", analysis.entityType),
            )

            self._link_games(article_id, analysis)
            self._link_franchises(article_id, analysis)

            initial_topic_ready = self._has_initial_topic_analysis(analysis)
            initial_importance_score = None
            if initial_topic_ready:
                official_bonus, source_bonus, community_penalty, initial_importance_score = (
                    topic_analysis_service.score_initial_importance(
                        analysis.semanticImportanceScore,
                        article.sourceName,
                        article.sourceType,
                    )
                )
                logger.info(
                    "[ArticleAnalysis] 새 Topic 초기 중요도 계산 - articleId=%s semantic=%s "
                    "officialBonus=%s sourceBonus=%s communityPenalty=%s final=%s",
                    article_id,
                    analysis.semanticImportanceScore,
                    official_bonus,
                    source_bonus,
                    community_penalty,
                    initial_importance_score,
                )

            topic_result = topic_integration_service.integrate(
                article,
                analysis,
                initial_importance_score=initial_importance_score,
                initial_why_important=analysis.whyImportant if initial_topic_ready else None,
            )
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
                game_news_relevant=analysis.gameNewsRelevant,
                entity_type=analysis.entityType,
            )

            logger.info(
                "[ArticleAnalysis] 분석 저장 완료 - articleId=%s status=%s",
                article_id,
                completed.analysisStatus.value,
            )

            if not self._should_reanalyze_topic(
                topic_result.action,
                initial_topic_ready,
            ):
                logger.info(
                    "[ArticleAnalysis] 단일 기사 새 Topic은 Article AI 초기 분석 재사용 - "
                    "articleId=%s topicId=%s TopicAnalyzer=SKIPPED",
                    article_id,
                    topic_result.topicId,
                )
            else:
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

    def _should_reanalyze_topic(
        self,
        action: TopicIntegrationAction,
        initial_topic_ready: bool,
    ) -> bool:
        return not (
            action == TopicIntegrationAction.CREATED_NEW
            and initial_topic_ready
        )

    def _has_initial_topic_analysis(self, analysis) -> bool:
        if not getattr(analysis, "gameNewsRelevant", False):
            return False
        title = (getattr(analysis, "topicTitle", None) or "").strip()
        why_important = (getattr(analysis, "whyImportant", None) or "").strip()
        semantic_score = getattr(analysis, "semanticImportanceScore", None)
        return bool(title and why_important and semantic_score is not None)

    def _link_games(self, article_id: int, analysis) -> None:
        article_entity_type = self._entity_type(
            getattr(analysis, "entityType", ArticleEntityType.MIXED),
            ArticleEntityType.MIXED,
        )
        if article_entity_type in {
            ArticleEntityType.FRANCHISE,
            ArticleEntityType.UNNAMED_ENTRY,
            ArticleEntityType.NONE,
        }:
            logger.info(
                "[ArticleAnalysis] 기사 범위가 특정 게임이 아니므로 Game 연결 생략 - "
                "articleId=%s entityType=%s",
                article_id,
                article_entity_type.value,
            )
            return

        for related_game in analysis.relatedGames:
            related_entity_type = self._entity_type(
                getattr(related_game, "entityType", ArticleEntityType.SPECIFIC_GAME),
                ArticleEntityType.SPECIFIC_GAME,
            )
            if related_entity_type != ArticleEntityType.SPECIFIC_GAME:
                logger.info(
                    "[ArticleAnalysis] 특정 Game 판별이 아니므로 resolve 생략 - "
                    "articleId=%s game=%s entityType=%s confidence=%.2f",
                    article_id,
                    related_game.name,
                    related_entity_type.value,
                    related_game.confidenceScore,
                )
                continue

            result = news_client.resolve_game_entity(
                article_id=article_id,
                detected_name=related_game.name,
                entity_type=related_entity_type.value,
                is_primary=related_game.isPrimary,
                confidence_score=related_game.confidenceScore,
                reason=related_game.reason,
            )
            if result.outcome == EntityResolutionOutcome.AUTO_LINKED:
                logger.info(
                    "[ArticleAnalysis] Game 자동 확정/연결 - articleId=%s gameId=%s name=%s confidence=%.2f",
                    article_id, result.gameId, related_game.name, related_game.confidenceScore,
                )
            elif result.outcome == EntityResolutionOutcome.REVIEW_REQUIRED:
                logger.info(
                    "[ArticleAnalysis] Game 관리자 검토 전환 - articleId=%s reviewId=%s name=%s confidence=%.2f",
                    article_id, result.reviewId, related_game.name, related_game.confidenceScore,
                )
            else:
                logger.info(
                    "[ArticleAnalysis] Game 신뢰도 부족으로 무시 - articleId=%s name=%s confidence=%.2f",
                    article_id, related_game.name, related_game.confidenceScore,
                )

    def _link_franchises(self, article_id: int, analysis) -> None:
        article_entity_type = self._entity_type(
            getattr(analysis, "entityType", ArticleEntityType.MIXED),
            ArticleEntityType.MIXED,
        )
        if article_entity_type in {ArticleEntityType.SPECIFIC_GAME, ArticleEntityType.NONE}:
            logger.info(
                "[ArticleAnalysis] 기사 범위가 Franchise가 아니므로 Franchise 연결 생략 - "
                "articleId=%s entityType=%s",
                article_id,
                article_entity_type.value,
            )
            return

        for related_franchise in analysis.relatedFranchises:
            related_entity_type = self._entity_type(
                getattr(related_franchise, "entityType", ArticleEntityType.FRANCHISE),
                ArticleEntityType.FRANCHISE,
            )
            if related_entity_type not in {
                ArticleEntityType.FRANCHISE,
                ArticleEntityType.UNNAMED_ENTRY,
            }:
                logger.info(
                    "[ArticleAnalysis] Franchise 범위 판별이 아니므로 resolve 생략 - "
                    "articleId=%s franchise=%s entityType=%s confidence=%.2f",
                    article_id,
                    related_franchise.name,
                    related_entity_type.value,
                    related_franchise.confidenceScore,
                )
                continue

            result = news_client.resolve_franchise_entity(
                article_id=article_id,
                detected_name=related_franchise.name,
                entity_type=related_entity_type.value,
                is_primary=related_franchise.isPrimary,
                confidence_score=related_franchise.confidenceScore,
                reason=related_franchise.reason,
            )
            if result.outcome == EntityResolutionOutcome.AUTO_LINKED:
                logger.info(
                    "[ArticleAnalysis] Franchise 자동 확정/연결 - articleId=%s franchiseId=%s name=%s confidence=%.2f",
                    article_id, result.franchiseId, related_franchise.name, related_franchise.confidenceScore,
                )
            elif result.outcome == EntityResolutionOutcome.REVIEW_REQUIRED:
                logger.info(
                    "[ArticleAnalysis] Franchise 관리자 검토 전환 - articleId=%s reviewId=%s name=%s confidence=%.2f",
                    article_id, result.reviewId, related_franchise.name, related_franchise.confidenceScore,
                )
            else:
                logger.info(
                    "[ArticleAnalysis] Franchise 신뢰도 부족으로 무시 - articleId=%s name=%s confidence=%.2f",
                    article_id, related_franchise.name, related_franchise.confidenceScore,
                )

    def _entity_type(self, value, default: ArticleEntityType) -> ArticleEntityType:
        if isinstance(value, ArticleEntityType):
            return value
        try:
            return ArticleEntityType(str(value))
        except (TypeError, ValueError):
            return default

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
