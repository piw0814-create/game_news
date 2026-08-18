import json
import logging
import threading
import time

from kafka import KafkaConsumer, TopicPartition

from app.config.settings import settings
from app.service.article_analysis_service import article_analysis_service

logger = logging.getLogger(__name__)


class NewsCreatedConsumer:
    """Kafka news.created 이벤트를 받아 기사 AI 분석을 실행한다."""

    def __init__(self):
        self.topic = settings.kafka_topic_news_created
        self.consumer = None
        self._running = False

    def start(self):
        self._running = True
        thread = threading.Thread(target=self._consume, daemon=True)
        thread.start()
        logger.info("[KafkaConsumer] 시작 - topic: %s", self.topic)

    def stop(self):
        self._running = False
        if self.consumer:
            self.consumer.close()

    def _consume(self):
        try:
            self.consumer = KafkaConsumer(
                self.topic,
                bootstrap_servers=settings.kafka_bootstrap_servers,
                group_id=settings.kafka_consumer_group_id,
                auto_offset_reset="earliest",
                enable_auto_commit=False,
                value_deserializer=lambda m: json.loads(m.decode("utf-8")),
                consumer_timeout_ms=1000,
            )

            while self._running:
                for message in self.consumer:
                    if not self._running:
                        break

                    should_commit = self._handle_message(message.value)
                    if should_commit:
                        self.consumer.commit()
                        logger.info(
                            "[KafkaConsumer] offset commit - partition=%s offset=%s",
                            message.partition,
                            message.offset,
                        )
                        continue

                    # 처리 상태조차 News Service에 기록하지 못한 경우 같은 메시지를 재시도한다.
                    partition = TopicPartition(message.topic, message.partition)
                    self.consumer.seek(partition, message.offset)
                    logger.warning(
                        "[KafkaConsumer] 처리 실패로 offset 미커밋/재시도 - partition=%s offset=%s",
                        message.partition,
                        message.offset,
                    )
                    time.sleep(settings.kafka_retry_delay_seconds)
                    break

        except Exception as exc:
            logger.exception("[KafkaConsumer] 오류 발생: %s", exc)
        finally:
            if self.consumer:
                self.consumer.close()

    def _handle_message(self, event: dict) -> bool:
        try:
            article_id = event.get("articleId")
            if article_id is None:
                logger.warning(
                    "[KafkaConsumer] articleId 없는 이벤트 무시 - event=%s",
                    event,
                )
                return True

            logger.info(
                "[KafkaConsumer] news.created 수신 - articleId=%s",
                article_id,
            )
            return article_analysis_service.process(int(article_id))
        except (TypeError, ValueError):
            logger.warning(
                "[KafkaConsumer] 잘못된 articleId 이벤트 무시 - event=%s",
                event,
            )
            return True
        except Exception as exc:
            logger.exception(
                "[KafkaConsumer] 메시지 처리 실패 - error=%s event=%s",
                exc,
                event,
            )
            return False


news_created_consumer = NewsCreatedConsumer()
