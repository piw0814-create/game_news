import logging
import threading
import time

from app.client.news_client import news_client
from app.config.settings import settings
from app.model.schemas import AnalysisStatus
from app.service.article_analysis_service import article_analysis_service

logger = logging.getLogger(__name__)


class ArticleRecoveryService:
    """미완료 기사 분석을 batch + circuit breaker 방식으로 복구한다."""

    def __init__(self) -> None:
        self._stop_event = threading.Event()
        self._periodic_thread: threading.Thread | None = None
        self._periodic_lock = threading.Lock()

    def recover(self) -> None:
        """Insight 시작 직후 실행하는 startup recovery."""
        if not settings.analysis_recovery_enabled:
            logger.info("[ArticleRecovery] 비활성화 - startup skip")
            return

        self._run_recovery(
            mode="startup",
            max_total=settings.analysis_recovery_max_total,
            pending_stale_minutes=0,
            query_retry=True,
        )

    def start_periodic(self) -> None:
        if not settings.analysis_recovery_enabled:
            logger.info("[ArticleRecovery] 비활성화 - periodic skip")
            return
        if not settings.analysis_recovery_periodic_enabled:
            logger.info("[ArticleRecovery] periodic 비활성화 - skip")
            return
        if self._periodic_thread and self._periodic_thread.is_alive():
            return

        self._stop_event.clear()
        self._periodic_thread = threading.Thread(
            target=self._periodic_loop,
            name="article-recovery-periodic",
            daemon=True,
        )
        self._periodic_thread.start()
        logger.info(
            "[ArticleRecovery] periodic 시작 - intervalSeconds=%s maxTotal=%s pendingStaleMinutes=%s",
            settings.analysis_recovery_periodic_interval_seconds,
            settings.analysis_recovery_periodic_max_total,
            settings.analysis_recovery_periodic_pending_stale_minutes,
        )

    def stop_periodic(self) -> None:
        self._stop_event.set()
        thread = self._periodic_thread
        if thread and thread.is_alive():
            thread.join(timeout=2.0)
        self._periodic_thread = None

    def _periodic_loop(self) -> None:
        interval = max(60, settings.analysis_recovery_periodic_interval_seconds)
        while not self._stop_event.wait(interval):
            if not self._periodic_lock.acquire(blocking=False):
                logger.info("[ArticleRecovery] periodic 이전 회차 진행 중 - skip")
                continue
            try:
                self._run_recovery(
                    mode="periodic",
                    max_total=settings.analysis_recovery_periodic_max_total,
                    pending_stale_minutes=(
                        settings.analysis_recovery_periodic_pending_stale_minutes
                    ),
                    query_retry=False,
                )
            except Exception as exc:
                logger.exception("[ArticleRecovery] periodic 복구 실패 - error=%s", exc)
            finally:
                self._periodic_lock.release()

    def _run_recovery(
        self,
        *,
        mode: str,
        max_total: int,
        pending_stale_minutes: int,
        query_retry: bool,
    ) -> None:
        batch_size = max(1, settings.analysis_recovery_limit)
        max_total = max(1, max_total)
        failure_threshold = max(
            1,
            settings.analysis_recovery_circuit_breaker_consecutive_failures,
        )

        attempted_ids: set[int] = set()
        completed_count = 0
        failed_count = 0
        consecutive_failures = 0
        circuit_open = False

        logger.info(
            "[ArticleRecovery] 복구 회차 시작 - mode=%s batch=%s maxTotal=%s pendingStaleMinutes=%s failureThreshold=%s",
            mode,
            batch_size,
            max_total,
            pending_stale_minutes,
            failure_threshold,
        )

        while len(attempted_ids) < max_total:
            request_limit = min(batch_size, max_total - len(attempted_ids))
            candidates = self._get_candidates(
                limit=request_limit,
                pending_stale_minutes=pending_stale_minutes,
                exclude_ids=attempted_ids,
                query_retry=query_retry,
            )
            if candidates is None:
                break
            if not candidates:
                break

            logger.info(
                "[ArticleRecovery] 복구 batch 조회 - mode=%s count=%s attempted=%s/%s",
                mode,
                len(candidates),
                len(attempted_ids),
                max_total,
            )

            for article in candidates:
                if len(attempted_ids) >= max_total:
                    break
                if article.id in attempted_ids:
                    continue

                attempted_ids.add(article.id)
                logger.info(
                    "[ArticleRecovery] 재분석 시작 - mode=%s articleId=%s status=%s",
                    mode,
                    article.id,
                    article.analysisStatus.value,
                )

                success = self._recover_article(article.id)
                if success:
                    completed_count += 1
                    consecutive_failures = 0
                    continue

                failed_count += 1
                consecutive_failures += 1
                logger.warning(
                    "[ArticleRecovery] 연속 복구 실패 - mode=%s consecutive=%s/%s articleId=%s",
                    mode,
                    consecutive_failures,
                    failure_threshold,
                    article.id,
                )
                if consecutive_failures >= failure_threshold:
                    circuit_open = True
                    logger.warning(
                        "[ArticleRecovery] circuit open - mode=%s attempted=%s completed=%s failed=%s. "
                        "남은 대상은 다음 recovery 회차로 이월합니다.",
                        mode,
                        len(attempted_ids),
                        completed_count,
                        failed_count,
                    )
                    break

            if circuit_open:
                break

            # 서버가 request_limit보다 적게 반환했다면 현재 조건에 맞는 미시도 대상이 없다.
            if len(candidates) < request_limit:
                break

        logger.info(
            "[ArticleRecovery] 복구 회차 완료 - mode=%s attempted=%s completed=%s failed=%s circuitOpen=%s",
            mode,
            len(attempted_ids),
            completed_count,
            failed_count,
            circuit_open,
        )

    def _recover_article(self, article_id: int) -> bool:
        try:
            processed = article_analysis_service.process(article_id)
            if not processed:
                logger.warning(
                    "[ArticleRecovery] 재분석 보류 - articleId=%s News Service 상태 기록 실패",
                    article_id,
                )
                return False

            latest = news_client.get_news(article_id)
            if latest.analysisStatus == AnalysisStatus.COMPLETED:
                logger.info(
                    "[ArticleRecovery] 재분석 성공 - articleId=%s",
                    article_id,
                )
                return True

            logger.warning(
                "[ArticleRecovery] 재분석 미완료 - articleId=%s status=%s",
                article_id,
                latest.analysisStatus.value,
            )
            return False
        except Exception as exc:
            logger.exception(
                "[ArticleRecovery] 재분석 예외 - articleId=%s error=%s",
                article_id,
                exc,
            )
            return False

    def _get_candidates(
        self,
        *,
        limit: int,
        pending_stale_minutes: int,
        exclude_ids: set[int],
        query_retry: bool,
    ):
        max_attempts = (
            max(1, settings.analysis_recovery_startup_retry_count)
            if query_retry
            else 1
        )

        for attempt in range(1, max_attempts + 1):
            try:
                return news_client.get_recovery_candidates(
                    limit=limit,
                    processing_stale_minutes=(
                        settings.analysis_recovery_processing_stale_minutes
                    ),
                    pending_stale_minutes=pending_stale_minutes,
                    exclude_ids=sorted(exclude_ids),
                )
            except Exception as exc:
                if attempt >= max_attempts:
                    logger.exception(
                        "[ArticleRecovery] 복구 대상 조회 최종 실패 - modeRetry=%s attempt=%s/%s error=%s",
                        query_retry,
                        attempt,
                        max_attempts,
                        exc,
                    )
                    return None

                logger.warning(
                    "[ArticleRecovery] 복구 대상 조회 실패 - attempt=%s/%s, %.1f초 후 재시도 - error=%s",
                    attempt,
                    max_attempts,
                    settings.analysis_recovery_startup_retry_delay_seconds,
                    exc,
                )
                time.sleep(settings.analysis_recovery_startup_retry_delay_seconds)

        return None


article_recovery_service = ArticleRecoveryService()
