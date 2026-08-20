package com.gamenews.news.controller;

import com.gamenews.news.common.ApiResponse;
import com.gamenews.news.dto.TopicArticleDto;
import com.gamenews.news.service.TopicArticleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/topics/{topicId}/articles")
@RequiredArgsConstructor
public class TopicArticleController {

    private final TopicArticleService topicArticleService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<TopicArticleDto.TopicArticleResponse>>> getArticlesByTopic(
            @PathVariable Long topicId) {
        return ResponseEntity.ok(ApiResponse.success(topicArticleService.getArticlesByTopic(topicId)));
    }
}
