import json
import logging
import threading
import time
from datetime import datetime, timezone

from kafka import KafkaConsumer, TopicPartition

from app.config.settings import settings
from app.kafka.dead_letter import dead_letter_publisher
from app.service.article_analysis_service import article_analysis_service

logger = logging.getLogger(__name__)


class NewsCreatedConsumer:
    """Kafka news.created 이벤트를 받아 기사 AI 분석을 실행한다."""

    def __init__(self):
        self.topic = settings.kafka_topic_news_created
        self.consumer = None
        self._running = False
        self._thread = None
        self._reconnect_delay_seconds = settings.kafka_reconnect_initial_delay_seconds
        self._connected = False
        self._last_consumed_at = None
        self._last_commit_at = None
        self._last_error_at = None
        self._last_error = None

    def start(self):
        if self._thread and self._thread.is_alive():
            logger.info("[KafkaConsumer] 이미 실행 중 - topic: %s", self.topic)
            return

        self._running = True
        self._reconnect_delay_seconds = settings.kafka_reconnect_initial_delay_seconds
        self._thread = threading.Thread(
            target=self._run_supervisor,
            name="news-created-consumer",
            daemon=True,
        )
        self._thread.start()
        logger.info("[KafkaConsumer] 시작 - topic: %s", self.topic)

    def stop(self):
        self._running = False
        if self._thread and self._thread.is_alive():
            self._thread.join(timeout=2.5)
        dead_letter_publisher.close()

    def _run_supervisor(self):
        while self._running:
            try:
                self.consumer = self._create_consumer()
                self._connected = True
                logger.info(
                    "[KafkaConsumer] Kafka 연결 완료 - topic=%s group=%s",
                    self.topic,
                    settings.kafka_consumer_group_id,
                )
                self._consume_session(self.consumer)
            except Exception as exc:
                if not self._running:
                    break

                delay = self._reconnect_delay_seconds
                self._record_error(exc)
                logger.exception(
                    "[KafkaConsumer] consumer 오류 - %.1f초 후 재연결: %s",
                    delay,
                    exc,
                )
                self._close_consumer()
                self._sleep_interruptibly(delay)
                self._reconnect_delay_seconds = min(
                    max(delay * 2, settings.kafka_reconnect_initial_delay_seconds),
                    settings.kafka_reconnect_max_delay_seconds,
                )
            finally:
                self._close_consumer()

    def _create_consumer(self):
        return KafkaConsumer(
            self.topic,
            bootstrap_servers=settings.kafka_bootstrap_servers,
            group_id=settings.kafka_consumer_group_id,
            auto_offset_reset="earliest",
            enable_auto_commit=False,
            max_poll_records=1,
            value_deserializer=None,
        )

    def _consume_session(self, consumer):
        while self._running:
            records = consumer.poll(timeout_ms=1000, max_records=1)
            if not records:
                continue

            retry_current_message = False
            for messages in records.values():
                for message in messages:
                    if not self._running:
                        return
                    if not self._process_message(consumer, message):
                        retry_current_message = True
                        break
                if retry_current_message:
                    break

    def _process_message(self, consumer, message) -> bool:
        self._last_consumed_at = self._now_iso()
        event, poison_error = self._decode_event(message.value)
        if poison_error is not None:
            if dead_letter_publisher.publish(message, poison_error):
                consumer.commit()
                self._last_commit_at = self._now_iso()
                self._mark_consumer_healthy()
                logger.warning(
                    "[KafkaConsumer] poison event DLQ 후 offset commit - partition=%s offset=%s error=%s",
                    message.partition,
                    message.offset,
                    poison_error,
                )
                return True

            self._seek_for_retry(consumer, message, "DLQ 저장 실패")
            return False

        should_commit = self._handle_event(event)
        if should_commit:
            consumer.commit()
            self._last_commit_at = self._now_iso()
            self._mark_consumer_healthy()
            logger.info(
                "[KafkaConsumer] offset commit - partition=%s offset=%s",
                message.partition,
                message.offset,
            )
            return True

        self._seek_for_retry(consumer, message, "처리 상태 기록 실패")
        return False

    @staticmethod
    def _decode_event(raw_value):
        try:
            if not isinstance(raw_value, (bytes, bytearray)):
                return None, "payload is not raw bytes"

            text = bytes(raw_value).decode("utf-8")
            event = json.loads(text)
        except UnicodeDecodeError as exc:
            return None, f"invalid utf-8 payload: {exc}"
        except json.JSONDecodeError as exc:
            return None, f"invalid json payload: {exc.msg}"

        if not isinstance(event, dict):
            return None, "event root must be a JSON object"

        article_id = event.get("articleId")
        if article_id is None:
            return None, "articleId is required"
        if isinstance(article_id, bool):
            return None, "articleId must be a positive integer"

        try:
            normalized_article_id = int(article_id)
        except (TypeError, ValueError):
            return None, "articleId must be a positive integer"

        if normalized_article_id <= 0:
            return None, "articleId must be a positive integer"

        event = dict(event)
        event["articleId"] = normalized_article_id
        return event, None

    def _handle_event(self, event: dict) -> bool:
        try:
            article_id = event["articleId"]
            logger.info(
                "[KafkaConsumer] news.created 수신 - articleId=%s",
                article_id,
            )
            return article_analysis_service.process(article_id)
        except Exception as exc:
            logger.exception(
                "[KafkaConsumer] 메시지 처리 실패 - error=%s event=%s",
                exc,
                event,
            )
            return False

    def _seek_for_retry(self, consumer, message, reason: str):
        partition = TopicPartition(message.topic, message.partition)
        consumer.seek(partition, message.offset)
        logger.warning(
            "[KafkaConsumer] offset 미커밋/재시도 - partition=%s offset=%s reason=%s",
            message.partition,
            message.offset,
            reason,
        )
        self._sleep_interruptibly(settings.kafka_retry_delay_seconds)

    def _mark_consumer_healthy(self):
        self._reconnect_delay_seconds = settings.kafka_reconnect_initial_delay_seconds

    def get_operational_status(self) -> dict:
        thread = self._thread
        return {
            "alive": bool(thread and thread.is_alive()),
            "connected": self._connected,
            "lastConsumedAt": self._last_consumed_at,
            "lastCommitAt": self._last_commit_at,
            "lastErrorAt": self._last_error_at,
            "lastError": self._last_error,
        }

    def _record_error(self, error: Exception) -> None:
        self._last_error_at = self._now_iso()
        message = str(error).strip() or error.__class__.__name__
        self._last_error = message[:1000]

    @staticmethod
    def _now_iso() -> str:
        return datetime.now(timezone.utc).isoformat()

    def _sleep_interruptibly(self, seconds: float):
        deadline = time.monotonic() + max(0.0, seconds)
        while self._running and time.monotonic() < deadline:
            time.sleep(min(0.25, max(0.0, deadline - time.monotonic())))

    def _close_consumer(self):
        self._connected = False
        consumer = self.consumer
        self.consumer = None
        if consumer is None:
            return
        try:
            consumer.close()
        except Exception:
            logger.debug("[KafkaConsumer] consumer close 실패", exc_info=True)


news_created_consumer = NewsCreatedConsumer()
