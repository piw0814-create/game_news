package com.gamenews.news.dto;

import com.gamenews.news.entity.Topic;
import com.gamenews.news.enums.NewsCategory;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public class TopicIntegrationDto {

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CandidateRequest {

        @NotNull(message = "기사 ID는 필수입니다")
        private Long articleId;

        @Min(value = 1, message = "후보 검색 시간은 1시간 이상이어야 합니다")
        @Max(value = 168, message = "후보 검색 시간은 168시간 이하여야 합니다")
        private int windowHours;

        @Min(value = 1, message = "후보 개수는 1개 이상이어야 합니다")
        @Max(value = 50, message = "후보 개수는 50개 이하여야 합니다")
        private int limit;

        /**
         * Game/Franchise 엔티티로 후보를 좁힐 수 없는 산업/정책 기사에서만
         * 최근 Topic fallback을 허용한다. 미지정 시 기존 호출자 호환을 위해 true.
         */
        @Builder.Default
        private boolean allowRecentFallback = true;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CandidateResponse {
        private Long id;
        private String title;
        private String summary;
        private NewsCategory category;
        private OffsetDateTime firstSeenAt;
        private OffsetDateTime lastUpdatedAt;

        public static CandidateResponse from(Topic topic) {
            return CandidateResponse.builder()
                    .id(topic.getId())
                    .title(topic.getTitle())
                    .summary(topic.getSummary())
                    .category(topic.getCategory())
                    .firstSeenAt(toUtc(topic.getFirstSeenAt()))
                    .lastUpdatedAt(toUtc(topic.getLastUpdatedAt()))
                    .build();
        }
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class IntegrateRequest {

        @NotNull(message = "기사 ID는 필수입니다")
        private Long articleId;

        private Long targetTopicId;
        private String title;
        private String summary;
        private NewsCategory category;

        @Min(value = 0, message = "초기 중요도는 0 이상이어야 합니다")
        @Max(value = 100, message = "초기 중요도는 100 이하여야 합니다")
        private Integer initialImportanceScore;

        private String initialWhyImportant;
    }

    public enum IntegrationAction {
        ALREADY_LINKED,
        LINKED_EXISTING,
        CREATED_NEW
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class IntegrateResponse {
        private Long topicId;
        private IntegrationAction action;
    }

    private static OffsetDateTime toUtc(LocalDateTime value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }

}
