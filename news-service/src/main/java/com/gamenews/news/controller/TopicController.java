package com.gamenews.news.controller;

import com.gamenews.news.common.ApiResponse;
import com.gamenews.news.dto.TopicDto;
import com.gamenews.news.service.TopicService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/topics")
@RequiredArgsConstructor
public class TopicController {

    private final TopicService topicService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<TopicDto.TopicResponse>>> getAllTopics() {
        return ResponseEntity.ok(ApiResponse.success(topicService.getAllTopics()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TopicDto.TopicDetailResponse>> getTopic(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(topicService.getTopic(id)));
    }
}
