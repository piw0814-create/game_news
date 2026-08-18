package com.gamenews.news.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.gamenews.news.entity.TopicGame;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public class TopicGameDto {

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CreateRequest {

        @NotNull(message = "게임 ID는 필수입니다")
        private Long gameId;

        @JsonProperty("isPrimary")
        private boolean isPrimary;

        @DecimalMin(value = "0.0", message = "관련도는 0 이상이어야 합니다")
        @DecimalMax(value = "1.0", message = "관련도는 1 이하여야 합니다")
        private BigDecimal relevanceScore;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties("primary")
    public static class TopicGameResponse {
        private Long id;
        private Long topicId;
        private Long gameId;
        private String gameName;
        @JsonProperty("isPrimary")
        private boolean isPrimary;
        private BigDecimal relevanceScore;
        private OffsetDateTime createdAt;

        public static TopicGameResponse from(TopicGame topicGame) {
            return TopicGameResponse.builder()
                    .id(topicGame.getId())
                    .topicId(topicGame.getTopic().getId())
                    .gameId(topicGame.getGame().getId())
                    .gameName(topicGame.getGame().getName())
                    .isPrimary(topicGame.isPrimary())
                    .relevanceScore(topicGame.getRelevanceScore())
                    .createdAt(toUtc(topicGame.getCreatedAt()))
                    .build();
        }
    }

    private static OffsetDateTime toUtc(LocalDateTime value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }

}
