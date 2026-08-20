package com.gamenews.news.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.gamenews.news.entity.FranchiseAlias;
import com.gamenews.news.entity.TopicFranchise;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

public class TopicFranchiseDto {


    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties("primary")
    public static class TopicFranchiseResponse {
        private Long id;
        private Long topicId;
        private Long franchiseId;
        private String franchiseName;
        private String franchiseDisplayName;
        private List<String> aliases;

        @JsonProperty("isPrimary")
        private boolean isPrimary;

        private BigDecimal relevanceScore;
        private OffsetDateTime createdAt;

        public static TopicFranchiseResponse from(TopicFranchise relation) {
            return TopicFranchiseResponse.builder()
                    .id(relation.getId())
                    .topicId(relation.getTopic().getId())
                    .franchiseId(relation.getFranchise().getId())
                    .franchiseName(relation.getFranchise().getName())
                    .franchiseDisplayName(relation.getFranchise().getDisplayName())
                    .aliases(relation.getFranchise().getAliases().stream()
                            .map(FranchiseAlias::getAlias)
                            .toList())
                    .isPrimary(relation.isPrimary())
                    .relevanceScore(relation.getRelevanceScore())
                    .createdAt(toUtc(relation.getCreatedAt()))
                    .build();
        }
    }

    private static OffsetDateTime toUtc(LocalDateTime value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }
}
