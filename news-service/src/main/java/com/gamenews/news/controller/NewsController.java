package com.gamenews.news.controller;

import com.gamenews.news.common.ApiResponse;
import com.gamenews.news.dto.NewsArticleDto;
import com.gamenews.news.service.NewsService;
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
@RequestMapping("/api/news")
@RequiredArgsConstructor
public class NewsController {

    private final NewsService newsService;

    @PostMapping
    public ResponseEntity<ApiResponse<NewsArticleDto.NewsArticleResponse>> createNews(
            @Valid @RequestBody NewsArticleDto.CreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(newsService.createNews(request)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<NewsArticleDto.NewsArticleResponse>>> getAllNews() {
        return ResponseEntity.ok(ApiResponse.success(newsService.getAllNews()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<NewsArticleDto.NewsArticleResponse>> getNews(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(newsService.getNews(id)));
    }
}
