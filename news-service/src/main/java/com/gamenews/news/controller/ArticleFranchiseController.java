package com.gamenews.news.controller;

import com.gamenews.news.common.ApiResponse;
import com.gamenews.news.dto.ArticleFranchiseDto;
import com.gamenews.news.service.ArticleFranchiseService;
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
@RequestMapping("/api/news/{articleId}/franchises")
@RequiredArgsConstructor
public class ArticleFranchiseController {

    private final ArticleFranchiseService articleFranchiseService;

    @PostMapping
    public ResponseEntity<ApiResponse<ArticleFranchiseDto.ArticleFranchiseResponse>> linkFranchise(
            @PathVariable Long articleId,
            @Valid @RequestBody ArticleFranchiseDto.CreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(articleFranchiseService.linkFranchise(articleId, request)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ArticleFranchiseDto.ArticleFranchiseResponse>>> getFranchisesByArticle(
            @PathVariable Long articleId) {
        return ResponseEntity.ok(ApiResponse.success(
                articleFranchiseService.getFranchisesByArticle(articleId)));
    }
}
