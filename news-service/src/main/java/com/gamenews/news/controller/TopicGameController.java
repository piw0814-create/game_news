package com.gamenews.news.controller;

import com.gamenews.news.common.ApiResponse;
import com.gamenews.news.dto.TopicGameDto;
import com.gamenews.news.service.TopicGameService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/topics/{topicId}/games")
@RequiredArgsConstructor
public class TopicGameController {

    private final TopicGameService topicGameService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<TopicGameDto.TopicGameResponse>>> getGamesByTopic(
            @PathVariable Long topicId) {
        return ResponseEntity.ok(ApiResponse.success(topicGameService.getGamesByTopic(topicId)));
    }
}
