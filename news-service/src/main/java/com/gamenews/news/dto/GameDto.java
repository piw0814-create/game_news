package com.gamenews.news.dto;

import com.gamenews.news.entity.Game;
import com.gamenews.news.entity.GameAlias;
import com.gamenews.news.entity.GameEnrichmentStatus;
import com.gamenews.news.entity.GameMetadataSource;
import com.gamenews.news.entity.GameRegistrationSource;
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

public class GameDto {

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CreateRequest {

        @NotBlank(message = "게임 이름은 필수입니다")
        @Size(max = 255, message = "게임 이름은 255자 이하여야 합니다")
        private String name;

        @Size(max = 255, message = "표시 이름은 255자 이하여야 합니다")
        private String displayName;

        private List<@Size(max = 255, message = "별칭은 255자 이하여야 합니다") String> aliases;

        @Size(max = 255, message = "퍼블리셔는 255자 이하여야 합니다")
        private String publisher;

        @Size(max = 255, message = "개발사는 255자 이하여야 합니다")
        private String developer;

        @Size(max = 100, message = "장르는 100자 이하여야 합니다")
        private String genre;

        @Size(max = 255, message = "플랫폼은 255자 이하여야 합니다")
        private String platform;

        @Size(max = 1000, message = "이미지 URL은 1000자 이하여야 합니다")
        private String imageUrl;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AdminUpdateRequest {

        @Size(max = 255, message = "게임 이름은 255자 이하여야 합니다")
        private String name;

        @Size(max = 255, message = "표시 이름은 255자 이하여야 합니다")
        private String displayName;

        private List<@Size(max = 255, message = "별칭은 255자 이하여야 합니다") String> aliases;

        @Size(max = 255, message = "퍼블리셔는 255자 이하여야 합니다")
        private String publisher;

        @Size(max = 255, message = "개발사는 255자 이하여야 합니다")
        private String developer;

        @Size(max = 100, message = "장르는 100자 이하여야 합니다")
        private String genre;

        @Size(max = 255, message = "플랫폼은 255자 이하여야 합니다")
        private String platform;

        @Size(max = 1000, message = "이미지 URL은 1000자 이하여야 합니다")
        private String imageUrl;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MergeRequest {

        @NotNull(message = "병합 대상 게임 ID는 필수입니다")
        private Long targetGameId;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class GameResponse {
        private Long id;
        private String name;
        private String displayName;
        private List<String> aliases;
        private String publisher;
        private String developer;
        private String genre;
        private String platform;
        private String imageUrl;
        private Long igdbId;
        private String igdbGameType;
        private Long versionParentIgdbId;
        private GameMetadataSource metadataSource;
        private GameEnrichmentStatus enrichmentStatus;
        private OffsetDateTime lastEnrichedAt;
        private GameRegistrationSource registrationSource;
        private OffsetDateTime createdAt;
        private OffsetDateTime updatedAt;

        public static GameResponse from(Game game) {
            return GameResponse.builder()
                    .id(game.getId())
                    .name(game.getName())
                    .displayName(game.getDisplayName())
                    .aliases(game.getAliases().stream().map(GameAlias::getAlias).toList())
                    .publisher(game.getPublisher())
                    .developer(game.getDeveloper())
                    .genre(game.getGenre())
                    .platform(game.getPlatform())
                    .imageUrl(game.getImageUrl())
                    .igdbId(game.getIgdbId())
                    .igdbGameType(game.getIgdbGameType())
                    .versionParentIgdbId(game.getVersionParentIgdbId())
                    .metadataSource(game.getMetadataSource())
                    .enrichmentStatus(game.getEnrichmentStatus() == null
                            ? GameEnrichmentStatus.NOT_ENRICHED
                            : game.getEnrichmentStatus())
                    .lastEnrichedAt(toUtc(game.getLastEnrichedAt()))
                    .registrationSource(game.getRegistrationSource())
                    .createdAt(toUtc(game.getCreatedAt()))
                    .updatedAt(toUtc(game.getUpdatedAt()))
                    .build();
        }
    }

    private static OffsetDateTime toUtc(LocalDateTime value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }
}
