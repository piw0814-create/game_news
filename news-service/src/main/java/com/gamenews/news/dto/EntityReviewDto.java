package com.gamenews.news.dto;

import com.gamenews.news.entity.EntityReview;
import com.gamenews.news.entity.EntityReviewKind;
import com.gamenews.news.entity.EntityReviewStatus;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class EntityReviewDto {

    public enum ResolutionOutcome {
        AUTO_LINKED,
        REVIEW_REQUIRED,
        IGNORED
    }

    public enum ResolutionType {
        GAME,
        FRANCHISE,
        UNRELATED
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class InternalResolveRequest {
        @NotNull
        private Long articleId;

        @NotBlank
        private String detectedName;

        private String entityType;

        private boolean primary;

        @NotNull
        @DecimalMin("0.0")
        @DecimalMax("1.0")
        private BigDecimal confidenceScore;

        private String reason;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class InternalResolveResponse {
        private ResolutionOutcome outcome;
        private Long reviewId;
        private Long gameId;
        private Long franchiseId;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Candidate {
        private String source;
        private EntityReviewKind entityKind;
        private Long localId;
        private Long igdbId;
        private Long igdbCollectionId;
        private String name;
        private String displayName;
        private String publisher;
        private String developer;
        private String gameType;
        private Integer releaseYear;
        private Long versionParentIgdbId;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AdminResponse {
        private Long id;
        private Long articleId;
        private String articleTitle;
        private String articleSourceName;
        private String articleUrl;
        private EntityReviewKind entityKind;
        private String detectedName;
        private String aiEntityType;
        private boolean primary;
        private BigDecimal confidenceScore;
        private String reason;
        private EntityReviewStatus status;
        private List<Candidate> candidates;
        private Long resolvedGameId;
        private Long resolvedFranchiseId;
        private LocalDateTime resolvedAt;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public static AdminResponse from(EntityReview review, List<Candidate> candidates) {
            return AdminResponse.builder()
                    .id(review.getId())
                    .articleId(review.getArticle().getId())
                    .articleTitle(review.getArticle().getTitle())
                    .articleSourceName(review.getArticle().getSourceName())
                    .articleUrl(review.getArticle().getUrl())
                    .entityKind(review.getEntityKind())
                    .detectedName(review.getDetectedName())
                    .aiEntityType(review.getAiEntityType())
                    .primary(review.isPrimary())
                    .confidenceScore(review.getConfidenceScore())
                    .reason(review.getReason())
                    .status(review.getStatus())
                    .candidates(candidates)
                    .resolvedGameId(review.getResolvedGameId())
                    .resolvedFranchiseId(review.getResolvedFranchiseId())
                    .resolvedAt(review.getResolvedAt())
                    .createdAt(review.getCreatedAt())
                    .updatedAt(review.getUpdatedAt())
                    .build();
        }
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdminResolveRequest {
        @NotNull
        private ResolutionType resolutionType;

        private Long localEntityId;
        private Long igdbId;
        private Long igdbCollectionId;
    }
}
