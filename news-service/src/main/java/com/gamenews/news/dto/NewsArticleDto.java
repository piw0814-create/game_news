package com.gamenews.news.dto;

import com.gamenews.news.entity.NewsArticle;
import com.gamenews.news.enums.AnalysisStatus;
import com.gamenews.news.enums.NewsCategory;
import com.gamenews.news.enums.SourceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

public class NewsArticleDto {

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CreateRequest {

        @NotBlank(message = "기사 제목은 필수입니다")
        @Size(max = 500, message = "기사 제목은 500자 이하여야 합니다")
        private String title;

        @NotBlank(message = "기사 URL은 필수입니다")
        @Size(max = 768, message = "기사 URL은 768자 이하여야 합니다")
        private String url;

        @NotBlank(message = "출처 이름은 필수입니다")
        @Size(max = 255, message = "출처 이름은 255자 이하여야 합니다")
        private String sourceName;

        @NotNull(message = "출처 유형은 필수입니다")
        private SourceType sourceType;

        private LocalDateTime publishedAt;

        private String content;

        private NewsCategory category;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class NewsArticleResponse {
        private Long id;
        private String title;
        private String url;
        private String sourceName;
        private SourceType sourceType;
        private LocalDateTime publishedAt;
        private LocalDateTime collectedAt;
        private String content;
        private NewsCategory category;
        private AnalysisStatus analysisStatus;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public static NewsArticleResponse from(NewsArticle article) {
            return NewsArticleResponse.builder()
                    .id(article.getId())
                    .title(article.getTitle())
                    .url(article.getUrl())
                    .sourceName(article.getSourceName())
                    .sourceType(article.getSourceType())
                    .publishedAt(article.getPublishedAt())
                    .collectedAt(article.getCollectedAt())
                    .content(article.getContent())
                    .category(article.getCategory())
                    .analysisStatus(article.getAnalysisStatus())
                    .createdAt(article.getCreatedAt())
                    .updatedAt(article.getUpdatedAt())
                    .build();
        }
    }
}
