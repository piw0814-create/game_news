package com.gamenews.interest.dto;

import com.gamenews.interest.entity.UserGame;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public class InterestDto {

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class GameSummary {
        private Long id;
        private String name;
        private String publisher;
        private String genre;
        private String platform;
        private String imageUrl;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class InterestResponse {
        private Long id;
        private Long gameId;
        private String gameName;
        private String publisher;
        private String genre;
        private String platform;
        private String imageUrl;
        private OffsetDateTime createdAt;

        public static InterestResponse from(UserGame userGame, GameSummary game) {
            return InterestResponse.builder()
                    .id(userGame.getId())
                    .gameId(userGame.getGameId())
                    .gameName(game.getName())
                    .publisher(game.getPublisher())
                    .genre(game.getGenre())
                    .platform(game.getPlatform())
                    .imageUrl(game.getImageUrl())
                    .createdAt(toUtc(userGame.getCreatedAt()))
                    .build();
        }
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ApiResponse<T> {
        private boolean success;
        private String message;
        private T data;

        public static <T> ApiResponse<T> success(T data) {
            return ApiResponse.<T>builder()
                    .success(true)
                    .message("성공")
                    .data(data)
                    .build();
        }

        public static <T> ApiResponse<T> error(String message) {
            return ApiResponse.<T>builder()
                    .success(false)
                    .message(message)
                    .data(null)
                    .build();
        }
    }

    private static OffsetDateTime toUtc(LocalDateTime value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }

}
