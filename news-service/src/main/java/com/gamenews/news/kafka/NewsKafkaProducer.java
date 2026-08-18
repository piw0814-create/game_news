package com.gamenews.news.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class NewsKafkaProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topics.news-created}")
    private String newsCreatedTopic;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishNewsCreated(NewsCreatedEvent event) {
        log.info("[Kafka Producer] news.created 발행 - articleId={}", event.articleId());

        kafkaTemplate.send(newsCreatedTopic, event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error(
                                "[Kafka Producer] news.created 발행 실패 - articleId={}",
                                event.articleId(),
                                ex
                        );
                        return;
                    }

                    log.info(
                            "[Kafka Producer] news.created 발행 성공 - articleId={}, partition={}, offset={}",
                            event.articleId(),
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset()
                    );
                });
    }
}
