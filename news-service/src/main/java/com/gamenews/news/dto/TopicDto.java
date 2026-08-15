package com.gamenews.news.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.gamenews.news.entity.Topic;
import com.gamenews.news.entity.TopicArticle;
import com.gamenews.news.entity.TopicGame;
import com.gamenews.news.enums.NewsCategory;
import com.gamenews.news.enums.SourceType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class TopicDto {

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CreateRequest {

        @NotBlank(message = "Topic 제목은 필수입니다")
        @Size(max = 500, message = "Topic 제목은 500자 이하여야 합니다")
        private String title;

        private String summary;

        private String whyImportant;

        private NewsCategory category;

        @Min(value = 0, message = "중요도는 0 이상이어야 합니다")
        @Max(value = 100, message = "중요도는 100 이하여야 합니다")
        private Integer importanceScore;

        private LocalDateTime firstSeenAt;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TopicResponse {
        private Long id;
        private String title;
        private String summary;
        private String whyImportant;
        private NewsCategory category;
        private Integer importanceScore;
        private LocalDateTime firstSeenAt;
        private LocalDateTime lastUpdatedAt;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public static TopicResponse from(Topic topic) {
            return TopicResponse.builder()
                    .id(topic.getId())
                    .title(topic.getTitle())
                    .summary(topic.getSummary())
                    .whyImportant(topic.getWhyImportant())
                    .category(topic.getCategory())
                    .importanceScore(topic.getImportanceScore())
                    .firstSeenAt(topic.getFirstSeenAt())
                    .lastUpdatedAt(topic.getLastUpdatedAt())
                    .createdAt(topic.getCreatedAt())
                    .updatedAt(topic.getUpdatedAt())
                    .build();
        }
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties("primary")
    public static class GameSummary {
        private Long id;
        private String name;

        @JsonProperty("isPrimary")
        private boolean isPrimary;

        private BigDecimal relevanceScore;

        public static GameSummary from(TopicGame topicGame) {
            return GameSummary.builder()
                    .id(topicGame.getGame().getId())
                    .name(topicGame.getGame().getName())
                    .isPrimary(topicGame.isPrimary())
                    .relevanceScore(topicGame.getRelevanceScore())
                    .build();
        }
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ArticleSummary {
        private Long id;
        private String title;
        private String sourceName;
        private SourceType sourceType;
        private String url;

        public static ArticleSummary from(TopicArticle topicArticle) {
            return ArticleSummary.builder()
                    .id(topicArticle.getArticle().getId())
                    .title(topicArticle.getArticle().getTitle())
                    .sourceName(topicArticle.getArticle().getSourceName())
                    .sourceType(topicArticle.getArticle().getSourceType())
                    .url(topicArticle.getArticle().getUrl())
                    .build();
        }
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TopicDetailResponse {
        private Long id;
        private String title;
        private String summary;
        private String whyImportant;
        private NewsCategory category;
        private Integer importanceScore;
        private List<GameSummary> games;
        private List<ArticleSummary> articles;
        private LocalDateTime firstSeenAt;
        private LocalDateTime lastUpdatedAt;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public static TopicDetailResponse from(
                Topic topic,
                List<TopicGame> topicGames,
                List<TopicArticle> topicArticles) {
            return TopicDetailResponse.builder()
                    .id(topic.getId())
                    .title(topic.getTitle())
                    .summary(topic.getSummary())
                    .whyImportant(topic.getWhyImportant())
                    .category(topic.getCategory())
                    .importanceScore(topic.getImportanceScore())
                    .games(topicGames.stream()
                            .map(GameSummary::from)
                            .toList())
                    .articles(topicArticles.stream()
                            .map(ArticleSummary::from)
                            .toList())
                    .firstSeenAt(topic.getFirstSeenAt())
                    .lastUpdatedAt(topic.getLastUpdatedAt())
                    .createdAt(topic.getCreatedAt())
                    .updatedAt(topic.getUpdatedAt())
                    .build();
        }
    }
}
