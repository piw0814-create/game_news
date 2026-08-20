package com.gamenews.news.controller;

import com.gamenews.news.common.ApiResponse;
import com.gamenews.news.dto.TopicFranchiseDto;
import com.gamenews.news.service.TopicFranchiseService;
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
@RequestMapping("/api/topics/{topicId}/franchises")
@RequiredArgsConstructor
public class TopicFranchiseController {

    private final TopicFranchiseService topicFranchiseService;

    @PostMapping
    public ResponseEntity<ApiResponse<TopicFranchiseDto.TopicFranchiseResponse>> linkFranchise(
            @PathVariable Long topicId,
            @Valid @RequestBody TopicFranchiseDto.CreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(topicFranchiseService.linkFranchise(topicId, request)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<TopicFranchiseDto.TopicFranchiseResponse>>> getFranchisesByTopic(
            @PathVariable Long topicId) {
        return ResponseEntity.ok(ApiResponse.success(topicFranchiseService.getFranchisesByTopic(topicId)));
    }
}
