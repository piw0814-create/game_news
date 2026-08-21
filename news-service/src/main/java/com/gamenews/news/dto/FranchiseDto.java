package com.gamenews.news.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.gamenews.news.entity.Franchise;
import com.gamenews.news.entity.FranchiseAlias;
import com.gamenews.news.entity.FranchiseMetadataSource;
import com.gamenews.news.entity.GameFranchise;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

public class FranchiseDto {

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class FranchiseResponse {
        private Long id;
        private String name;
        private String displayName;
        private List<String> aliases;
        private Long igdbId;
        private Long igdbCollectionId;
        private FranchiseMetadataSource metadataSource;
        private OffsetDateTime createdAt;
        private OffsetDateTime updatedAt;

        public static FranchiseResponse from(Franchise franchise) {
            return FranchiseResponse.builder()
                    .id(franchise.getId())
                    .name(franchise.getName())
                    .displayName(franchise.getDisplayName())
                    .aliases(franchise.getAliases().stream().map(FranchiseAlias::getAlias).toList())
                    .igdbId(franchise.getIgdbId())
                    .igdbCollectionId(franchise.getIgdbCollectionId())
                    .metadataSource(franchise.getMetadataSource())
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
    public static class GameFranchiseResponse {
        private Long id;
        private Long gameId;
        private Long franchiseId;
        private String franchiseName;
        private String franchiseDisplayName;
        @JsonProperty("isPrimary")
        private boolean isPrimary;
        private OffsetDateTime createdAt;

        public static GameFranchiseResponse from(GameFranchise relation) {
            return GameFranchiseResponse.builder()
                    .id(relation.getId())
                    .gameId(relation.getGame().getId())
                    .franchiseId(relation.getFranchise().getId())
                    .franchiseName(relation.getFranchise().getName())
                    .franchiseDisplayName(relation.getFranchise().getDisplayName())
                    .isPrimary(relation.isPrimary())
                    .createdAt(toUtc(relation.getCreatedAt()))
                    .build();
        }
    }

    private static OffsetDateTime toUtc(LocalDateTime value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }
}
