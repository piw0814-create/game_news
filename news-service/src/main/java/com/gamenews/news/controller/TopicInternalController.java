package com.gamenews.news.controller;

import com.gamenews.news.common.ApiResponse;
import com.gamenews.news.dto.TopicAnalysisDto;
import com.gamenews.news.dto.TopicDto;
import com.gamenews.news.dto.TopicIntegrationDto;
import com.gamenews.news.service.TopicAnalysisService;
import com.gamenews.news.service.TopicIntegrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/internal/topics")
@RequiredArgsConstructor
public class TopicInternalController {

    private final TopicIntegrationService topicIntegrationService;
    private final TopicAnalysisService topicAnalysisService;

    @PostMapping("/candidates")
    public ResponseEntity<ApiResponse<List<TopicIntegrationDto.CandidateResponse>>> getCandidates(
            @Valid @RequestBody TopicIntegrationDto.CandidateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(topicIntegrationService.getCandidates(request)));
    }

    @GetMapping("/by-article/{articleId}")
    public ResponseEntity<ApiResponse<TopicIntegrationDto.IntegrateResponse>> getExistingIntegration(
            @PathVariable Long articleId) {
        return ResponseEntity.ok(ApiResponse.success(
                topicIntegrationService.getExistingIntegration(articleId)));
    }

    @PostMapping("/integrate")
    public ResponseEntity<ApiResponse<TopicIntegrationDto.IntegrateResponse>> integrate(
            @Valid @RequestBody TopicIntegrationDto.IntegrateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(topicIntegrationService.integrate(request)));
    }

    @GetMapping("/{topicId}/analysis-context")
    public ResponseEntity<ApiResponse<TopicAnalysisDto.ContextResponse>> getAnalysisContext(
            @PathVariable Long topicId) {
        return ResponseEntity.ok(ApiResponse.success(topicAnalysisService.getAnalysisContext(topicId)));
    }

    @PutMapping("/{topicId}/analysis")
    public ResponseEntity<ApiResponse<TopicDto.TopicResponse>> updateAnalysis(
            @PathVariable Long topicId,
            @Valid @RequestBody TopicAnalysisDto.AnalysisUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(topicAnalysisService.updateAnalysis(topicId, request)));
    }
}
