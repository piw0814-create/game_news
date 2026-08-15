package com.gamenews.news.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.gamenews.news.entity.ArticleGame;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ArticleGameDto {

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CreateRequest {

        @NotNull(message = "게임 ID는 필수입니다")
        private Long gameId;

        @JsonProperty("isPrimary")
        private boolean isPrimary;

        @DecimalMin(value = "0.0", message = "신뢰도는 0 이상이어야 합니다")
        @DecimalMax(value = "1.0", message = "신뢰도는 1 이하여야 합니다")
        private BigDecimal confidenceScore;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties("primary")
    public static class ArticleGameResponse {
        private Long id;
        private Long articleId;
        private Long gameId;
        private String gameName;
        @JsonProperty("isPrimary")
        private boolean isPrimary;
        private BigDecimal confidenceScore;
        private LocalDateTime createdAt;

        public static ArticleGameResponse from(ArticleGame articleGame) {
            return ArticleGameResponse.builder()
                    .id(articleGame.getId())
                    .articleId(articleGame.getArticle().getId())
                    .gameId(articleGame.getGame().getId())
                    .gameName(articleGame.getGame().getName())
                    .isPrimary(articleGame.isPrimary())
                    .confidenceScore(articleGame.getConfidenceScore())
                    .createdAt(articleGame.getCreatedAt())
                    .build();
        }
    }
}
