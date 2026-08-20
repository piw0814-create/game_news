package com.gamenews.news.service;

import com.gamenews.news.event.EntityReviewResolvedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
@RequiredArgsConstructor
@Slf4j
public class EntityReviewReanalysisService {

    private final InsightServiceClient insightServiceClient;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onResolved(EntityReviewResolvedEvent event) {
        if (event.topicId() == null) return;
        try {
            insightServiceClient.reanalyzeTopic(event.topicId());
        } catch (RuntimeException ex) {
            log.warn("[EntityReview] Topic reanalysis failed - topicId={}, reason={}",
                    event.topicId(), ex.getMessage());
        }
    }
}
