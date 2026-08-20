package com.gamenews.news.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.gamenews.news.entity.Franchise;
import com.gamenews.news.entity.FranchiseAlias;
import com.gamenews.news.entity.FranchiseMetadataSource;
import com.gamenews.news.entity.GameFranchise;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

public class FranchiseAdminDto {

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateRequest {
        @NotBlank(message = "프랜차이즈 이름은 필수입니다")
        @Size(max = 255, message = "프랜차이즈 이름은 255자 이하여야 합니다")
        private String name;

        @Size(max = 255, message = "표시 이름은 255자 이하여야 합니다")
        private String displayName;

        private List<@Size(max = 255, message = "별칭은 255자 이하여야 합니다") String> aliases;
    }

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
    @Builder
    public static class SummaryResponse {
        private Long id;
        private String name;
        private String displayName;
        private List<String> aliases;
        private Long igdbId;
        private FranchiseMetadataSource metadataSource;
        private int gameCount;
        private OffsetDateTime createdAt;
        private OffsetDateTime updatedAt;

        public static SummaryResponse from(Franchise franchise, int gameCount) {
            return SummaryResponse.builder()
                    .id(franchise.getId())
                    .name(franchise.getName())
                    .displayName(franchise.getDisplayName())
                    .aliases(franchise.getAliases().stream().map(FranchiseAlias::getAlias).toList())
                    .igdbId(franchise.getIgdbId())
                    .metadataSource(franchise.getMetadataSource())
                    .gameCount(gameCount)
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
        private List<GameLinkResponse> games;
        private OffsetDateTime createdAt;
        private OffsetDateTime updatedAt;

        public static DetailResponse from(Franchise franchise, List<GameFranchise> links) {
            return DetailResponse.builder()
                    .id(franchise.getId())
                    .name(franchise.getName())
                    .displayName(franchise.getDisplayName())
                    .aliases(franchise.getAliases().stream().map(FranchiseAlias::getAlias).toList())
                    .igdbId(franchise.getIgdbId())
                    .metadataSource(franchise.getMetadataSource())
                    .games(links.stream().map(GameLinkResponse::from).toList())
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
        @JsonProperty("isPrimary")
        private boolean isPrimary;
        private OffsetDateTime createdAt;

        public static GameLinkResponse from(GameFranchise link) {
            return GameLinkResponse.builder()
                    .id(link.getId())
                    .gameId(link.getGame().getId())
                    .gameName(link.getGame().getName())
                    .gameDisplayName(link.getGame().getDisplayName())
                    .isPrimary(link.isPrimary())
                    .createdAt(toUtc(link.getCreatedAt()))
                    .build();
        }
    }

    private static OffsetDateTime toUtc(LocalDateTime value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }
}
