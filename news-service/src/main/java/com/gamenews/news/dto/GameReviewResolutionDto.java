package com.gamenews.news.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class GameReviewResolutionDto {

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResolveAsFranchiseRequest {
        @NotNull(message = "프랜차이즈 ID는 필수입니다")
        private Long franchiseId;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ResolveAsFranchiseResponse {
        private Long removedGameId;
        private Long franchiseId;
        private String franchiseName;
        private int convertedArticleCount;
    }
}
