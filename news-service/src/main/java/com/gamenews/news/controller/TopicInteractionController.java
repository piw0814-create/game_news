package com.gamenews.news.controller;

import com.gamenews.news.common.ApiResponse;
import com.gamenews.news.dto.TopicInteractionDto;
import com.gamenews.news.service.TopicInteractionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/topics/{topicId}")
@RequiredArgsConstructor
public class TopicInteractionController {

    private final TopicInteractionService topicInteractionService;

    @GetMapping("/comments")
    public ResponseEntity<ApiResponse<List<TopicInteractionDto.CommentResponse>>> getComments(
            @PathVariable Long topicId,
            @RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.ok(ApiResponse.success(
                topicInteractionService.getComments(topicId, userId)
        ));
    }

    @PostMapping("/comments")
    public ResponseEntity<ApiResponse<TopicInteractionDto.CommentResponse>> createComment(
            @PathVariable Long topicId,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader(value = "X-User-Email", required = false) String email,
            @Valid @RequestBody TopicInteractionDto.CommentCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        topicInteractionService.createComment(topicId, userId, email, request)
                ));
    }

    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<ApiResponse<Void>> deleteComment(
            @PathVariable Long topicId,
            @PathVariable Long commentId,
            @RequestHeader("X-User-Id") Long userId) {
        topicInteractionService.deleteComment(topicId, commentId, userId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/likes")
    public ResponseEntity<ApiResponse<TopicInteractionDto.LikeStatusResponse>> getLikeStatus(
            @PathVariable Long topicId,
            @RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.ok(ApiResponse.success(
                topicInteractionService.getLikeStatus(topicId, userId)
        ));
    }

    @PostMapping("/likes")
    public ResponseEntity<ApiResponse<TopicInteractionDto.LikeStatusResponse>> like(
            @PathVariable Long topicId,
            @RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(topicInteractionService.like(topicId, userId)));
    }

    @DeleteMapping("/likes")
    public ResponseEntity<ApiResponse<TopicInteractionDto.LikeStatusResponse>> unlike(
            @PathVariable Long topicId,
            @RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.ok(ApiResponse.success(
                topicInteractionService.unlike(topicId, userId)
        ));
    }
}
