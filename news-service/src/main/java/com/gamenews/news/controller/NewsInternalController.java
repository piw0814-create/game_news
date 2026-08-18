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

import java.util.List;

@RestController
@RequestMapping("/api/internal/news")
@RequiredArgsConstructor
public class NewsInternalController {

    private final NewsService newsService;

    @GetMapping("/recovery-candidates")
    public ResponseEntity<ApiResponse<List<NewsArticleDto.NewsArticleResponse>>> getRecoveryCandidates(
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "15") int processingStaleMinutes) {
        return ResponseEntity.ok(ApiResponse.success(
                newsService.getRecoveryCandidates(limit, processingStaleMinutes)));
    }
}
