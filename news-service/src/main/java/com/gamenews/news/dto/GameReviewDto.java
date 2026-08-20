package com.gamenews.news.dto;

import com.gamenews.news.entity.GameReviewStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public class GameReviewDto {

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ReviewContextResponse {
        private ArticleContext sourceArticle;
        private List<LinkedArticleContext> linkedArticles;
        private List<SimilarGameContext> similarGames;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ArticleContext {
        private Long id;
        private String title;
        private String sourceName;
        private String url;
        private OffsetDateTime publishedAt;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LinkedArticleContext {
        private Long id;
        private String title;
        private String sourceName;
        private String url;
        private OffsetDateTime publishedAt;
        private BigDecimal confidenceScore;
        private String relevanceReason;
        private boolean primary;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SimilarGameContext {
        private Long id;
        private String name;
        private String displayName;
        private List<String> aliases;
        private String publisher;
        private String developer;
        private GameReviewStatus reviewStatus;
        private BigDecimal similarityScore;
        private List<String> reasons;
    }
}
