package com.gamenews.news.dto;

import com.gamenews.news.entity.Game;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

public class GameDto {

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CreateRequest {

        @NotBlank(message = "게임 이름은 필수입니다")
        @Size(max = 255, message = "게임 이름은 255자 이하여야 합니다")
        private String name;

        @Size(max = 255, message = "퍼블리셔는 255자 이하여야 합니다")
        private String publisher;

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
    public static class GameResponse {
        private Long id;
        private String name;
        private String publisher;
        private String genre;
        private String platform;
        private String imageUrl;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public static GameResponse from(Game game) {
            return GameResponse.builder()
                    .id(game.getId())
                    .name(game.getName())
                    .publisher(game.getPublisher())
                    .genre(game.getGenre())
                    .platform(game.getPlatform())
                    .imageUrl(game.getImageUrl())
                    .createdAt(game.getCreatedAt())
                    .updatedAt(game.getUpdatedAt())
                    .build();
        }
    }
}
