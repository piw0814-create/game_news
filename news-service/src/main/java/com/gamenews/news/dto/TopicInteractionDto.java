package com.gamenews.news.dto;

import com.gamenews.news.entity.TopicComment;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public class TopicInteractionDto {

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CommentCreateRequest {

        @NotBlank(message = "댓글 내용을 입력해주세요")
        @Size(max = 1000, message = "댓글은 1000자 이하로 입력해주세요")
        private String content;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class CommentResponse {
        private Long id;
        private Long userId;
        private String authorName;
        private String content;
        private boolean mine;
        private OffsetDateTime createdAt;

        public static CommentResponse from(TopicComment comment, Long currentUserId) {
            return CommentResponse.builder()
                    .id(comment.getId())
                    .userId(comment.getUserId())
                    .authorName(comment.getAuthorName())
                    .content(comment.getContent())
                    .mine(comment.getUserId().equals(currentUserId))
                    .createdAt(toUtc(comment.getCreatedAt()))
                    .build();
        }
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class LikeStatusResponse {
        private long count;
        private boolean liked;
    }

    private static OffsetDateTime toUtc(LocalDateTime value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }
}
