package com.gamenews.news.dto;

import com.gamenews.news.entity.TopicArticle;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

public class TopicArticleDto {

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CreateRequest {

        @NotNull(message = "기사 ID는 필수입니다")
        private Long articleId;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TopicArticleResponse {
        private Long id;
        private Long topicId;
        private Long articleId;
        private String articleTitle;
        private String sourceName;
        private LocalDateTime createdAt;

        public static TopicArticleResponse from(TopicArticle topicArticle) {
            return TopicArticleResponse.builder()
                    .id(topicArticle.getId())
                    .topicId(topicArticle.getTopic().getId())
                    .articleId(topicArticle.getArticle().getId())
                    .articleTitle(topicArticle.getArticle().getTitle())
                    .sourceName(topicArticle.getArticle().getSourceName())
                    .createdAt(topicArticle.getCreatedAt())
                    .build();
        }
    }
}
