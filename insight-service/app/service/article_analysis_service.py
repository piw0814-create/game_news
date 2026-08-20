import logging

from app.client.news_client import NewsServiceError, news_client
from app.client.openai_client import openai_article_analyzer
from app.config.settings import settings
from app.model.schemas import AnalysisStatus, ArticleEntityType
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
            franchises = news_client.get_franchises()
            analysis = openai_article_analyzer.analyze(article, games, franchises)

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

            logger.info(
                "[ArticleAnalysis] 엔티티 범위 판정 - articleId=%s entityType=%s",
                article_id,
                getattr(analysis.entityType, "value", analysis.entityType),
            )

            self._link_games(article_id, analysis, games)
            self._link_franchises(article_id, analysis, franchises)

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

    def _link_games(self, article_id: int, analysis, games) -> None:
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

        game_by_name = {}
        for game in games:
            for identity in self._game_identities(game):
                game_by_name.setdefault(identity, game)

        existing_links = news_client.get_article_games(article_id)
        existing_game_ids = {link.gameId for link in existing_links}

        link_threshold = settings.game_review_create_confidence_threshold

        for related_game in analysis.relatedGames:
            related_entity_type = self._entity_type(
                getattr(related_game, "entityType", ArticleEntityType.SPECIFIC_GAME),
                ArticleEntityType.SPECIFIC_GAME,
            )
            if related_entity_type != ArticleEntityType.SPECIFIC_GAME:
                logger.info(
                    "[ArticleAnalysis] 특정 Game 판별이 아니므로 연결/자동등록 생략 - "
                    "articleId=%s game=%s entityType=%s confidence=%.2f reason=%s",
                    article_id,
                    related_game.name,
                    related_entity_type.value,
                    related_game.confidenceScore,
                    related_game.reason,
                )
                continue

            if related_game.confidenceScore < link_threshold:
                logger.info(
                    "[ArticleAnalysis] 게임 관련성 신뢰도 부족으로 연결 생략 - "
                    "articleId=%s game=%s confidence=%.2f threshold=%.2f reason=%s",
                    article_id,
                    related_game.name,
                    related_game.confidenceScore,
                    link_threshold,
                    related_game.reason,
                )
                continue

            matched = game_by_name.get(related_game.name.strip().casefold())
            if matched is None:
                matched = self._resolve_unregistered_game(article_id, related_game)
                if matched is None:
                    continue
                for identity in self._game_identities(matched):
                    game_by_name.setdefault(identity, matched)

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
                relevance_reason=related_game.reason,
            )
            existing_game_ids.add(matched.id)
            logger.info(
                "[ArticleAnalysis] ArticleGame 연결 - articleId=%s gameId=%s confidence=%.2f reason=%s",
                article_id,
                matched.id,
                related_game.confidenceScore,
                related_game.reason,
            )


    def _link_franchises(self, article_id: int, analysis, franchises) -> None:
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

        franchise_by_name = {}
        for franchise in franchises:
            for identity in self._franchise_identities(franchise):
                franchise_by_name.setdefault(identity, franchise)

        existing_links = news_client.get_article_franchises(article_id)
        existing_franchise_ids = {link.franchiseId for link in existing_links}
        link_threshold = settings.game_review_create_confidence_threshold

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
                    "[ArticleAnalysis] Franchise 범위 판별이 아니므로 연결 생략 - "
                    "articleId=%s franchise=%s entityType=%s confidence=%.2f reason=%s",
                    article_id,
                    related_franchise.name,
                    related_entity_type.value,
                    related_franchise.confidenceScore,
                    related_franchise.reason,
                )
                continue

            if related_franchise.confidenceScore < link_threshold:
                logger.info(
                    "[ArticleAnalysis] 프랜차이즈 관련성 신뢰도 부족으로 연결 생략 - "
                    "articleId=%s franchise=%s confidence=%.2f threshold=%.2f reason=%s",
                    article_id,
                    related_franchise.name,
                    related_franchise.confidenceScore,
                    link_threshold,
                    related_franchise.reason,
                )
                continue

            matched = franchise_by_name.get(related_franchise.name.strip().casefold())
            if matched is None:
                logger.info(
                    "[ArticleAnalysis] Known Franchise가 아니므로 연결 생략 - articleId=%s franchise=%s",
                    article_id,
                    related_franchise.name,
                )
                continue

            if matched.id in existing_franchise_ids:
                continue

            news_client.link_franchise(
                article_id=article_id,
                franchise_id=matched.id,
                is_primary=related_franchise.isPrimary,
                confidence_score=related_franchise.confidenceScore,
                relevance_reason=related_franchise.reason,
            )
            existing_franchise_ids.add(matched.id)
            logger.info(
                "[ArticleAnalysis] ArticleFranchise 연결 - articleId=%s franchiseId=%s confidence=%.2f reason=%s",
                article_id,
                matched.id,
                related_franchise.confidenceScore,
                related_franchise.reason,
            )

    def _franchise_identities(self, franchise):
        values = [franchise.name, franchise.displayName, *(franchise.aliases or [])]
        return {
            value.strip().casefold()
            for value in values
            if value and value.strip()
        }

    def _game_identities(self, game):
        values = [game.name, game.displayName, *(game.aliases or [])]
        return {
            value.strip().casefold()
            for value in values
            if value and value.strip()
        }

    def _resolve_unregistered_game(self, article_id: int, related_game):
        entity_type = self._entity_type(
            getattr(related_game, "entityType", ArticleEntityType.SPECIFIC_GAME),
            ArticleEntityType.SPECIFIC_GAME,
        )
        if entity_type != ArticleEntityType.SPECIFIC_GAME:
            logger.info(
                "[ArticleAnalysis] 특정 작품 식별이 아니므로 미등록 Game 생성 차단 - "
                "articleId=%s game=%s entityType=%s confidence=%.2f reason=%s",
                article_id,
                related_game.name,
                entity_type.value,
                related_game.confidenceScore,
                related_game.reason,
            )
            return None

        confidence = related_game.confidenceScore
        review_threshold = settings.game_review_create_confidence_threshold
        auto_threshold = settings.game_auto_create_confidence_threshold

        if confidence < review_threshold:
            logger.info(
                "[ArticleAnalysis] 미등록 게임 신뢰도 부족으로 생성 생략 - "
                "articleId=%s game=%s confidence=%.2f threshold=%.2f",
                article_id,
                related_game.name,
                confidence,
                review_threshold,
            )
            return None

        review_status = (
            "CONFIRMED"
            if confidence >= auto_threshold
            else "REVIEW_REQUIRED"
        )

        result = news_client.resolve_or_create_ai_game(
            name=related_game.name,
            review_status=review_status,
            registration_confidence=confidence,
            source_article_id=article_id,
        )

        logger.info(
            "[ArticleAnalysis] 미등록 게임 resolve-or-create - "
            "articleId=%s gameId=%s game=%s created=%s reviewStatus=%s confidence=%.2f",
            article_id,
            result.game.id,
            result.game.name,
            result.created,
            result.game.reviewStatus or review_status,
            confidence,
        )
        return result.game

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
