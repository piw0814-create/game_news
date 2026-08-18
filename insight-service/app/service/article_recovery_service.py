import logging
import time

from app.client.news_client import news_client
from app.config.settings import settings
from app.model.schemas import AnalysisStatus
from app.service.article_analysis_service import article_analysis_service

logger = logging.getLogger(__name__)


class ArticleRecoveryService:
    """Insight 재시작 시 미완료 기사 분석을 기존 파이프라인으로 복구한다."""

    def recover(self) -> None:
        if not settings.analysis_recovery_enabled:
            logger.info("[ArticleRecovery] 비활성화 - skip")
            return

        candidates = None
        max_attempts = max(1, settings.analysis_recovery_startup_retry_count)

        for attempt in range(1, max_attempts + 1):
            try:
                candidates = news_client.get_recovery_candidates(
                    limit=settings.analysis_recovery_limit,
                    processing_stale_minutes=(
                        settings.analysis_recovery_processing_stale_minutes
                    ),
                )
                break
            except Exception as exc:
                if attempt >= max_attempts:
                    # Recovery 조회 실패 때문에 Insight 전체 기동을 막지는 않는다.
                    logger.exception(
                        "[ArticleRecovery] 복구 대상 조회 최종 실패 - "
                        "attempt=%s/%s error=%s",
                        attempt,
                        max_attempts,
                        exc,
                    )
                    return

                logger.warning(
                    "[ArticleRecovery] 복구 대상 조회 실패 - "
                    "attempt=%s/%s, %.1f초 후 재시도 - error=%s",
                    attempt,
                    max_attempts,
                    settings.analysis_recovery_startup_retry_delay_seconds,
                    exc,
                )
                time.sleep(settings.analysis_recovery_startup_retry_delay_seconds)

        if candidates is None:
            return

        logger.info(
            "[ArticleRecovery] 복구 대상 조회 - count=%s limit=%s staleMinutes=%s",
            len(candidates),
            settings.analysis_recovery_limit,
            settings.analysis_recovery_processing_stale_minutes,
        )

        completed_count = 0
        failed_count = 0

        for article in candidates:
            logger.info(
                "[ArticleRecovery] 재분석 시작 - articleId=%s status=%s",
                article.id,
                article.analysisStatus.value,
            )

            try:
                processed = article_analysis_service.process(article.id)
                if not processed:
                    failed_count += 1
                    logger.warning(
                        "[ArticleRecovery] 재분석 보류 - articleId=%s "
                        "News Service 상태 기록 실패",
                        article.id,
                    )
                    continue

                latest = news_client.get_news(article.id)
                if latest.analysisStatus == AnalysisStatus.COMPLETED:
                    completed_count += 1
                    logger.info(
                        "[ArticleRecovery] 재분석 성공 - articleId=%s",
                        article.id,
                    )
                else:
                    failed_count += 1
                    logger.warning(
                        "[ArticleRecovery] 재분석 미완료 - articleId=%s status=%s",
                        article.id,
                        latest.analysisStatus.value,
                    )
            except Exception as exc:
                failed_count += 1
                logger.exception(
                    "[ArticleRecovery] 재분석 예외 - articleId=%s error=%s",
                    article.id,
                    exc,
                )

        logger.info(
            "[ArticleRecovery] 복구 완료 - attempted=%s completed=%s failed=%s",
            len(candidates),
            completed_count,
            failed_count,
        )


article_recovery_service = ArticleRecoveryService()
