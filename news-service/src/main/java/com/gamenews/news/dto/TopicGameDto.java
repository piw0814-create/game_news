package com.gamenews.news.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.gamenews.news.entity.TopicGame;
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
    @JsonIgnoreProperties("primary")
    public static class TopicGameResponse {
        private Long id;
        private Long topicId;
        private Long gameId;
        private String gameName;
        private String gameDisplayName;
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
                    .gameDisplayName(topicGame.getGame().getDisplayName())
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
