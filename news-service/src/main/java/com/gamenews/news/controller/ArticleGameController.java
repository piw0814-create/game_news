package com.gamenews.news.controller;

import com.gamenews.news.common.ApiResponse;
import com.gamenews.news.dto.ArticleGameDto;
import com.gamenews.news.service.ArticleGameService;
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
@RequestMapping("/api/news/{articleId}/games")
@RequiredArgsConstructor
public class ArticleGameController {

    private final ArticleGameService articleGameService;

    @PostMapping
    public ResponseEntity<ApiResponse<ArticleGameDto.ArticleGameResponse>> linkGame(
            @PathVariable Long articleId,
            @Valid @RequestBody ArticleGameDto.CreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(articleGameService.linkGame(articleId, request)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ArticleGameDto.ArticleGameResponse>>> getGamesByArticle(
            @PathVariable Long articleId) {
        return ResponseEntity.ok(ApiResponse.success(articleGameService.getGamesByArticle(articleId)));
    }
}
