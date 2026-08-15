package com.gamenews.news.controller;

import com.gamenews.news.common.ApiResponse;
import com.gamenews.news.dto.TopicArticleDto;
import com.gamenews.news.service.TopicArticleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/topics/{topicId}/articles")
@RequiredArgsConstructor
public class TopicArticleController {

    private final TopicArticleService topicArticleService;

    @PostMapping
    public ResponseEntity<ApiResponse<TopicArticleDto.TopicArticleResponse>> linkArticle(
            @PathVariable Long topicId,
            @Valid @RequestBody TopicArticleDto.CreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(topicArticleService.linkArticle(topicId, request)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<TopicArticleDto.TopicArticleResponse>>> getArticlesByTopic(
            @PathVariable Long topicId) {
        return ResponseEntity.ok(ApiResponse.success(topicArticleService.getArticlesByTopic(topicId)));
    }
}
