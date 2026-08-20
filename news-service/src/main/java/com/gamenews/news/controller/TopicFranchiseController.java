package com.gamenews.news.controller;

import com.gamenews.news.common.ApiResponse;
import com.gamenews.news.dto.TopicFranchiseDto;
import com.gamenews.news.service.TopicFranchiseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/topics/{topicId}/franchises")
@RequiredArgsConstructor
public class TopicFranchiseController {

    private final TopicFranchiseService topicFranchiseService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<TopicFranchiseDto.TopicFranchiseResponse>>> getFranchisesByTopic(
            @PathVariable Long topicId) {
        return ResponseEntity.ok(ApiResponse.success(topicFranchiseService.getFranchisesByTopic(topicId)));
    }
}
