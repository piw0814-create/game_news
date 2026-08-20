package com.gamenews.news.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.gamenews.news.entity.ArticleFranchise;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public class ArticleFranchiseDto {

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CreateRequest {

        @NotNull(message = "프랜차이즈 ID는 필수입니다")
        private Long franchiseId;

        @JsonProperty("isPrimary")
        private boolean isPrimary;

        @DecimalMin(value = "0.0", message = "신뢰도는 0 이상이어야 합니다")
        @DecimalMax(value = "1.0", message = "신뢰도는 1 이하여야 합니다")
        private BigDecimal confidenceScore;

        @Size(max = 1000, message = "관련성 근거는 1000자 이하여야 합니다")
        private String relevanceReason;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties("primary")
    public static class ArticleFranchiseResponse {
        private Long id;
        private Long articleId;
        private Long franchiseId;
        private String franchiseName;
        private String franchiseDisplayName;
        @JsonProperty("isPrimary")
        private boolean isPrimary;
        private BigDecimal confidenceScore;
        private String relevanceReason;
        private OffsetDateTime createdAt;

        public static ArticleFranchiseResponse from(ArticleFranchise relation) {
            return ArticleFranchiseResponse.builder()
                    .id(relation.getId())
                    .articleId(relation.getArticle().getId())
                    .franchiseId(relation.getFranchise().getId())
                    .franchiseName(relation.getFranchise().getName())
                    .franchiseDisplayName(relation.getFranchise().getDisplayName())
                    .isPrimary(relation.isPrimary())
                    .confidenceScore(relation.getConfidenceScore())
                    .relevanceReason(relation.getRelevanceReason())
                    .createdAt(toUtc(relation.getCreatedAt()))
                    .build();
        }
    }

    private static OffsetDateTime toUtc(LocalDateTime value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }
}
