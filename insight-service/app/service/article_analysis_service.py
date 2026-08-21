import logging

from app.client.news_client import NewsServiceError, news_client
from app.client.openai_client import openai_article_analyzer
from app.model.schemas import (
    AnalysisStatus,
    ArticleAnalysisResult,
    ArticleEntityType,
    EntityResolutionOutcome,
    NewsArticleResponse,
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

        체크포인트 정책:
        - PENDING/FAILED/stale PROCESSING: Article Analyzer부터 실행
        - ANALYZED: 저장된 AI 결과로 Entity Resolution부터 재개
        - TOPIC_PENDING: 저장된 AI 결과로 Topic Integration부터 재개
        - COMPLETED: skip

        ANALYZED/TOPIC_PENDING 이후의 장애는 AI 결과를 보존하므로 FAILED로 되돌리지 않는다.
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

        if article.analysisStatus == AnalysisStatus.ANALYZED:
            return self._resume_from_analyzed(article)

        if article.analysisStatus == AnalysisStatus.TOPIC_PENDING:
            return self._resume_from_topic_pending(article)

        return self._analyze_from_scratch(article)

    def _analyze_from_scratch(self, article: NewsArticleResponse) -> bool:
        article_id = article.id
        try:
            news_client.update_analysis_status(article_id, AnalysisStatus.PROCESSING)
            logger.info(
                "[ArticleAnalysis] 상태 변경 - articleId=%s status=PROCESSING",
                article_id,
            )

            analysis = openai_article_analyzer.analyze(article)

            # AI 결과를 다음 단계보다 먼저 저장한다. 여기까지 성공하면 이후 장애에서
            # Article Analyzer를 다시 호출하지 않아도 된다.
            article = news_client.save_analysis_checkpoint(article_id, analysis)
            logger.info(
                "[ArticleAnalysis] AI 체크포인트 저장 - articleId=%s status=%s",
                article_id,
                article.analysisStatus.value,
            )
        except Exception as exc:
            logger.exception(
                "[ArticleAnalysis] Article Analyzer/체크포인트 실패 - articleId=%s error=%s",
                article_id,
                exc,
            )
            # 체크포인트 이전 실패만 FAILED로 되돌려 전체 분석 재시도를 허용한다.
            return self._mark_failed(article_id)

        if not analysis.gameNewsRelevant:
            return self._complete_without_topic(article_id)

        return self._continue_from_analyzed(article, analysis)

    def _resume_from_analyzed(self, article: NewsArticleResponse) -> bool:
        try:
            analysis = news_client.get_analysis_checkpoint(article.id)
        except Exception as exc:
            # ANALYZED인데 체크포인트를 읽지 못했다고 AI를 다시 호출하면 비용 중복이 생긴다.
            # 상태를 보존하고 다음 recovery에서 다시 읽는다.
            logger.exception(
                "[ArticleAnalysis] ANALYZED 체크포인트 조회 실패 - AI 재호출 없이 보류 "
                "articleId=%s error=%s",
                article.id,
                exc,
            )
            return False

        logger.info(
            "[ArticleAnalysis] ANALYZED 체크포인트 재사용 - ArticleAnalyzer=SKIPPED articleId=%s",
            article.id,
        )
        if not analysis.gameNewsRelevant:
            return self._complete_without_topic(article.id)
        return self._continue_from_analyzed(article, analysis)

    def _continue_from_analyzed(
        self,
        article: NewsArticleResponse,
        analysis: ArticleAnalysisResult,
    ) -> bool:
        article_id = article.id
        try:
            logger.info(
                "[ArticleAnalysis] 엔티티 범위 판정 - articleId=%s entityType=%s",
                article_id,
                getattr(analysis.entityType, "value", analysis.entityType),
            )
            self._link_games(article_id, analysis)
            self._link_franchises(article_id, analysis)

            news_client.update_analysis_status(article_id, AnalysisStatus.TOPIC_PENDING)
            logger.info(
                "[ArticleAnalysis] 상태 변경 - articleId=%s status=TOPIC_PENDING",
                article_id,
            )
        except Exception as exc:
            # AI 체크포인트는 이미 ANALYZED로 저장되어 있다. 상태를 그대로 두면
            # Recovery가 Entity Resolution부터 다시 시작한다.
            logger.exception(
                "[ArticleAnalysis] Entity Resolution 보류 - AI 재호출 없음 "
                "articleId=%s status=ANALYZED error=%s",
                article_id,
                exc,
            )
            return True

        return self._continue_from_topic_pending(article, analysis)

    def _resume_from_topic_pending(self, article: NewsArticleResponse) -> bool:
        try:
            analysis = news_client.get_analysis_checkpoint(article.id)
        except Exception as exc:
            logger.exception(
                "[ArticleAnalysis] TOPIC_PENDING 체크포인트 조회 실패 - AI 재호출 없이 보류 "
                "articleId=%s error=%s",
                article.id,
                exc,
            )
            return False

        logger.info(
            "[ArticleAnalysis] TOPIC_PENDING 체크포인트 재사용 - "
            "ArticleAnalyzer=SKIPPED EntityResolution=SKIPPED articleId=%s",
            article.id,
        )
        return self._continue_from_topic_pending(article, analysis)

    def _continue_from_topic_pending(
        self,
        article: NewsArticleResponse,
        analysis: ArticleAnalysisResult,
    ) -> bool:
        article_id = article.id
        if not analysis.gameNewsRelevant:
            return self._complete_without_topic(article_id)

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

        try:
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
        except Exception as exc:
            # Matcher/API 장애를 새 Topic으로 바꾸지 않는다. TOPIC_PENDING을 유지해
            # 다음 Recovery가 이 단계부터 다시 실행한다.
            logger.exception(
                "[ArticleAnalysis] Topic 통합 보류 - ArticleAnalyzer 재호출 없음 "
                "articleId=%s status=TOPIC_PENDING error=%s",
                article_id,
                exc,
            )
            return True

        try:
            completed = news_client.update_analysis_status(
                article_id,
                AnalysisStatus.COMPLETED,
            )
            logger.info(
                "[ArticleAnalysis] 분석 완료 상태 저장 - articleId=%s status=%s",
                article_id,
                completed.analysisStatus.value,
            )
        except Exception as exc:
            # Topic 관계는 이미 DB에 저장됐다. TOPIC_PENDING으로 남겨두면 다음 회차에
            # existing Topic 조회가 먼저 동작하여 Matcher도 다시 호출하지 않는다.
            logger.exception(
                "[ArticleAnalysis] COMPLETED 상태 저장 보류 - Topic 관계 유지, AI 재호출 없음 "
                "articleId=%s topicId=%s error=%s",
                article_id,
                topic_result.topicId,
                exc,
            )
            return True

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

    def _complete_without_topic(self, article_id: int) -> bool:
        try:
            completed = news_client.update_analysis_status(
                article_id,
                AnalysisStatus.COMPLETED,
            )
            logger.info(
                "[ArticleAnalysis] 게임/IP 생태계 비관련 - Topic 생성 생략 "
                "articleId=%s status=%s",
                article_id,
                completed.analysisStatus.value,
            )
            return True
        except Exception as exc:
            logger.exception(
                "[ArticleAnalysis] 비관련 기사 COMPLETED 저장 실패 - "
                "ANALYZED 체크포인트 유지 articleId=%s error=%s",
                article_id,
                exc,
            )
            return True

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
