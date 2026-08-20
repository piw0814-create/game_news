package com.gamenews.news.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.gamenews.news.entity.ArticleFranchise;
import com.gamenews.news.entity.Franchise;
import com.gamenews.news.entity.FranchiseAlias;
import com.gamenews.news.entity.FranchiseMetadataSource;
import com.gamenews.news.entity.GameFranchise;
import com.gamenews.news.entity.GameFranchiseSource;
import com.gamenews.news.entity.TopicFranchise;
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
import java.util.List;

public class FranchiseAdminDto {

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateRequest {
        @Size(max = 255, message = "프랜차이즈 이름은 255자 이하여야 합니다")
        private String name;

        @Size(max = 255, message = "표시 이름은 255자 이하여야 합니다")
        private String displayName;

        private List<@Size(max = 255, message = "별칭은 255자 이하여야 합니다") String> aliases;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GameLinkRequest {
        @NotNull(message = "게임 ID는 필수입니다")
        private Long gameId;

        @JsonProperty("isPrimary")
        private boolean isPrimary;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GameLinkUpdateRequest {
        @JsonProperty("isPrimary")
        private boolean isPrimary;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MergeRequest {
        @NotNull(message = "병합 대상 프랜차이즈 ID는 필수입니다")
        private Long targetFranchiseId;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SummaryResponse {
        private Long id;
        private String name;
        private String displayName;
        private List<String> aliases;
        private Long igdbId;
        private FranchiseMetadataSource metadataSource;
        private int gameCount;
        private int articleCount;
        private int topicCount;
        private OffsetDateTime lastSyncedAt;
        private OffsetDateTime createdAt;
        private OffsetDateTime updatedAt;

        public static SummaryResponse from(
                Franchise franchise,
                int gameCount,
                int articleCount,
                int topicCount) {
            return SummaryResponse.builder()
                    .id(franchise.getId())
                    .name(franchise.getName())
                    .displayName(franchise.getDisplayName())
                    .aliases(franchise.getAliases().stream().map(FranchiseAlias::getAlias).toList())
                    .igdbId(franchise.getIgdbId())
                    .metadataSource(franchise.getMetadataSource())
                    .gameCount(gameCount)
                    .articleCount(articleCount)
                    .topicCount(topicCount)
                    .lastSyncedAt(toUtc(franchise.getLastSyncedAt()))
                    .createdAt(toUtc(franchise.getCreatedAt()))
                    .updatedAt(toUtc(franchise.getUpdatedAt()))
                    .build();
        }
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DetailResponse {
        private Long id;
        private String name;
        private String displayName;
        private List<String> aliases;
        private Long igdbId;
        private FranchiseMetadataSource metadataSource;
        private OffsetDateTime lastSyncedAt;
        private List<GameLinkResponse> games;
        private List<ArticleLinkResponse> articles;
        private List<TopicLinkResponse> topics;
        private List<SimilarFranchiseResponse> similarFranchises;
        private OffsetDateTime createdAt;
        private OffsetDateTime updatedAt;

        public static DetailResponse from(
                Franchise franchise,
                List<GameFranchise> gameLinks,
                List<ArticleFranchise> articleLinks,
                List<TopicFranchise> topicLinks,
                List<SimilarFranchiseResponse> similarFranchises) {
            return DetailResponse.builder()
                    .id(franchise.getId())
                    .name(franchise.getName())
                    .displayName(franchise.getDisplayName())
                    .aliases(franchise.getAliases().stream().map(FranchiseAlias::getAlias).toList())
                    .igdbId(franchise.getIgdbId())
                    .metadataSource(franchise.getMetadataSource())
                    .lastSyncedAt(toUtc(franchise.getLastSyncedAt()))
                    .games(gameLinks.stream().map(GameLinkResponse::from).toList())
                    .articles(articleLinks.stream().map(ArticleLinkResponse::from).toList())
                    .topics(topicLinks.stream().map(TopicLinkResponse::from).toList())
                    .similarFranchises(similarFranchises)
                    .createdAt(toUtc(franchise.getCreatedAt()))
                    .updatedAt(toUtc(franchise.getUpdatedAt()))
                    .build();
        }
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties("primary")
    public static class GameLinkResponse {
        private Long id;
        private Long gameId;
        private String gameName;
        private String gameDisplayName;
        private Long gameIgdbId;
        private String igdbGameType;
        private GameFranchiseSource relationSource;
        @JsonProperty("isPrimary")
        private boolean isPrimary;
        private OffsetDateTime createdAt;

        public static GameLinkResponse from(GameFranchise link) {
            return GameLinkResponse.builder()
                    .id(link.getId())
                    .gameId(link.getGame().getId())
                    .gameName(link.getGame().getName())
                    .gameDisplayName(link.getGame().getDisplayName())
                    .gameIgdbId(link.getGame().getIgdbId())
                    .igdbGameType(link.getGame().getIgdbGameType())
                    .relationSource(link.getSource())
                    .isPrimary(link.isPrimary())
                    .createdAt(toUtc(link.getCreatedAt()))
                    .build();
        }
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ArticleLinkResponse {
        private Long articleId;
        private String title;
        private String sourceName;
        private OffsetDateTime publishedAt;
        private BigDecimal confidenceScore;
        private String relevanceReason;
        @JsonProperty("isPrimary")
        private boolean isPrimary;

        public static ArticleLinkResponse from(ArticleFranchise link) {
            return ArticleLinkResponse.builder()
                    .articleId(link.getArticle().getId())
                    .title(link.getArticle().getTitle())
                    .sourceName(link.getArticle().getSourceName())
                    .publishedAt(toUtc(link.getArticle().getPublishedAt()))
                    .confidenceScore(link.getConfidenceScore())
                    .relevanceReason(link.getRelevanceReason())
                    .isPrimary(link.isPrimary())
                    .build();
        }
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TopicLinkResponse {
        private Long topicId;
        private String title;
        private Integer importanceScore;
        private OffsetDateTime lastUpdatedAt;
        private BigDecimal relevanceScore;
        @JsonProperty("isPrimary")
        private boolean isPrimary;

        public static TopicLinkResponse from(TopicFranchise link) {
            return TopicLinkResponse.builder()
                    .topicId(link.getTopic().getId())
                    .title(link.getTopic().getTitle())
                    .importanceScore(link.getTopic().getImportanceScore())
                    .lastUpdatedAt(toUtc(link.getTopic().getLastUpdatedAt()))
                    .relevanceScore(link.getRelevanceScore())
                    .isPrimary(link.isPrimary())
                    .build();
        }
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SimilarFranchiseResponse {
        private Long id;
        private String name;
        private String displayName;
        private Long igdbId;
        private BigDecimal similarityScore;
        private List<String> reasons;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SyncResponse {
        private Long franchiseId;
        private Long igdbId;
        private int igdbGameCount;
        private int createdGameCount;
        private int updatedGameCount;
        private int skippedGameCount;
        private int removedRelationCount;
        private OffsetDateTime lastSyncedAt;
    }

    public static OffsetDateTime toUtc(LocalDateTime value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }
}
