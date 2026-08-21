package com.gamenews.news.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

public class OperationalStatusDto {

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Response {
        private String status;
        private LocalDateTime generatedAt;
        private ArticleStatus articles;
        private EntityReviewStatus entityReviews;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ArticleStatus {
        private long pending;
        private long processing;
        private long analyzed;
        private long topicPending;
        private long completed;
        private long failed;
        private long staleProcessing;
        private int processingStaleMinutes;
        private LocalDateTime oldestPendingAt;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class EntityReviewStatus {
        private long pending;
    }
}
