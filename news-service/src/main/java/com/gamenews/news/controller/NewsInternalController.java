package com.gamenews.news.controller;

import com.gamenews.news.common.ApiResponse;
import com.gamenews.news.dto.NewsArticleDto;
import com.gamenews.news.service.NewsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/internal/news")
@RequiredArgsConstructor
public class NewsInternalController {

    private final NewsService newsService;

    @GetMapping("/latest-published-at")
    public ResponseEntity<ApiResponse<OffsetDateTime>> getLatestPublishedAt(
            @RequestParam String sourceName) {
        return ResponseEntity.ok(ApiResponse.success(
                newsService.getLatestPublishedAtBySource(sourceName)));
    }

    @GetMapping("/recovery-candidates")
    public ResponseEntity<ApiResponse<List<NewsArticleDto.NewsArticleResponse>>> getRecoveryCandidates(
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "15") int processingStaleMinutes,
            @RequestParam(defaultValue = "0") int pendingStaleMinutes,
            @RequestParam(required = false) List<Long> excludeIds) {
        return ResponseEntity.ok(ApiResponse.success(
                newsService.getRecoveryCandidates(
                        limit,
                        processingStaleMinutes,
                        pendingStaleMinutes,
                        excludeIds)));
    }
}
