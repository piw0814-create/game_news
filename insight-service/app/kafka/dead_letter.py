import json
import logging
import threading

from kafka import KafkaProducer

from app.config.settings import settings

logger = logging.getLogger(__name__)


class DeadLetterPublisher:
    """구조적으로 처리할 수 없는 Kafka 이벤트를 DLQ에 보관한다."""

    def __init__(self):
        self.topic = settings.kafka_dlq_topic
        self._producer = None
        self._lock = threading.Lock()

    def publish(self, message, error: str) -> bool:
        if not settings.kafka_dlq_enabled:
            logger.warning(
                "[KafkaDLQ] DLQ 비활성화 - poison event 건너뜀 topic=%s partition=%s offset=%s error=%s",
                message.topic,
                message.partition,
                message.offset,
                error,
            )
            return True

        payload = {
            "originalTopic": message.topic,
            "partition": message.partition,
            "offset": message.offset,
            "error": error,
            "payload": self._payload_text(message.value),
        }
        key = f"{message.topic}:{message.partition}:{message.offset}".encode("utf-8")

        try:
            producer = self._get_producer()
            metadata = producer.send(
                self.topic,
                key=key,
                value=payload,
            ).get(timeout=settings.kafka_dlq_send_timeout_seconds)
            logger.error(
                "[KafkaDLQ] poison event 저장 - dlq=%s partition=%s offset=%s original=%s:%s:%s error=%s",
                self.topic,
                metadata.partition,
                metadata.offset,
                message.topic,
                message.partition,
                message.offset,
                error,
            )
            return True
        except Exception as exc:
            logger.exception(
                "[KafkaDLQ] 저장 실패 - original=%s:%s:%s error=%s dlqError=%s",
                message.topic,
                message.partition,
                message.offset,
                error,
                exc,
            )
            self._reset_producer()
            return False

    def close(self):
        self._reset_producer()

    def _get_producer(self):
        with self._lock:
            if self._producer is None:
                timeout_ms = max(1000, int(settings.kafka_dlq_send_timeout_seconds * 1000))
                self._producer = KafkaProducer(
                    bootstrap_servers=settings.kafka_bootstrap_servers,
                    key_serializer=lambda value: value,
                    value_serializer=lambda value: json.dumps(
                        value,
                        ensure_ascii=False,
                        separators=(",", ":"),
                    ).encode("utf-8"),
                    request_timeout_ms=timeout_ms,
                    max_block_ms=timeout_ms,
                )
            return self._producer

    def _reset_producer(self):
        with self._lock:
            producer = self._producer
            self._producer = None
        if producer is not None:
            try:
                producer.close(timeout=settings.kafka_dlq_send_timeout_seconds)
            except Exception:
                logger.debug("[KafkaDLQ] producer close 실패", exc_info=True)

    @staticmethod
    def _payload_text(raw_value) -> str:
        if raw_value is None:
            return ""
        if isinstance(raw_value, bytes):
            return raw_value.decode("utf-8", errors="replace")
        return str(raw_value)


dead_letter_publisher = DeadLetterPublisher()
