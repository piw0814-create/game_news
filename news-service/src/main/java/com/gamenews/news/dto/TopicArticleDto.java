package com.gamenews.news.dto;

import com.gamenews.news.entity.TopicArticle;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public class TopicArticleDto {


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
        private OffsetDateTime createdAt;

        public static TopicArticleResponse from(TopicArticle topicArticle) {
            return TopicArticleResponse.builder()
                    .id(topicArticle.getId())
                    .topicId(topicArticle.getTopic().getId())
                    .articleId(topicArticle.getArticle().getId())
                    .articleTitle(topicArticle.getArticle().getTitle())
                    .sourceName(topicArticle.getArticle().getSourceName())
                    .createdAt(toUtc(topicArticle.getCreatedAt()))
                    .build();
        }
    }

    private static OffsetDateTime toUtc(LocalDateTime value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }

}
