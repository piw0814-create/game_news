package com.gamenews.news.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.gamenews.news.entity.GameAlias;
import com.gamenews.news.entity.Topic;
import com.gamenews.news.entity.TopicArticle;
import com.gamenews.news.entity.TopicGame;
import com.gamenews.news.enums.NewsCategory;
import com.gamenews.news.enums.SourceType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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

public class TopicAnalysisDto {

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TopicContext {
        private Long id;
        private String title;
        private String summary;
        private NewsCategory category;

        public static TopicContext from(Topic topic) {
            return TopicContext.builder()
                    .id(topic.getId())
                    .title(topic.getTitle())
                    .summary(topic.getSummary())
                    .category(topic.getCategory())
                    .build();
        }
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties("primary")
    public static class GameContext {
        private Long id;
        private String name;
        private String displayName;
        private List<String> aliases;
        private String publisher;
        private String genre;
        private String platform;

        @JsonProperty("isPrimary")
        private boolean isPrimary;

        public static GameContext from(TopicGame topicGame) {
            return GameContext.builder()
                    .id(topicGame.getGame().getId())
                    .name(topicGame.getGame().getName())
                    .displayName(topicGame.getGame().getDisplayName())
                    .aliases(topicGame.getGame().getAliases().stream().map(GameAlias::getAlias).toList())
                    .publisher(topicGame.getGame().getPublisher())
                    .genre(topicGame.getGame().getGenre())
                    .platform(topicGame.getGame().getPlatform())
                    .isPrimary(topicGame.isPrimary())
                    .build();
        }
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ArticleContext {
        private Long id;
        private String title;
        private String sourceName;
        private SourceType sourceType;
        private OffsetDateTime publishedAt;
        private OffsetDateTime collectedAt;
        private String summary;
        private NewsCategory category;

        public static ArticleContext from(TopicArticle topicArticle) {
            return ArticleContext.builder()
                    .id(topicArticle.getArticle().getId())
                    .title(topicArticle.getArticle().getTitle())
                    .sourceName(topicArticle.getArticle().getSourceName())
                    .sourceType(topicArticle.getArticle().getSourceType())
                    .publishedAt(toUtc(topicArticle.getArticle().getPublishedAt()))
                    .collectedAt(toUtc(topicArticle.getArticle().getCollectedAt()))
                    .summary(topicArticle.getArticle().getSummary())
                    .category(topicArticle.getArticle().getCategory())
                    .build();
        }
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ContextResponse {
        private TopicContext topic;
        private List<GameContext> games;
        private List<ArticleContext> articles;

        public static ContextResponse from(
                Topic topic,
                List<TopicGame> topicGames,
                List<TopicArticle> topicArticles) {
            return ContextResponse.builder()
                    .topic(TopicContext.from(topic))
                    .games(topicGames.stream().map(GameContext::from).toList())
                    .articles(topicArticles.stream().map(ArticleContext::from).toList())
                    .build();
        }
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AnalysisUpdateRequest {

        @NotBlank(message = "Topic 제목은 필수입니다")
        @Size(max = 500, message = "Topic 제목은 500자 이하여야 합니다")
        private String title;

        @NotBlank(message = "Topic 요약은 필수입니다")
        private String summary;

        @NotNull(message = "Topic 카테고리는 필수입니다")
        private NewsCategory category;

        @NotNull(message = "Topic 중요도는 필수입니다")
        @Min(value = 0, message = "중요도는 0 이상이어야 합니다")
        @Max(value = 100, message = "중요도는 100 이하여야 합니다")
        private Integer importanceScore;

        @NotBlank(message = "왜 중요한가는 필수입니다")
        private String whyImportant;
    }

    private static OffsetDateTime toUtc(LocalDateTime value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }

}
