package com.gamenews.collector.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

public class CollectorDto {

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class NewsCreateRequest {
        private String title;
        private String url;
        private String sourceName;
        private String sourceType;
        private LocalDateTime publishedAt;
        private String content;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CollectionResult {
        private String source;
        private int fetched;
        private int saved;
        private int skipped;
        private int failed;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ApiResponse<T> {
        private boolean success;
        private String message;
        private T data;

        public static <T> ApiResponse<T> success(String message, T data) {
            return ApiResponse.<T>builder()
                    .success(true)
                    .message(message)
                    .data(data)
                    .build();
        }

        public static <T> ApiResponse<T> error(String message) {
            return ApiResponse.<T>builder()
                    .success(false)
                    .message(message)
                    .build();
        }
    }
}
