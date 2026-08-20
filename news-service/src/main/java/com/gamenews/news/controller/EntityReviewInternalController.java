package com.gamenews.news.controller;

import com.gamenews.news.common.ApiResponse;
import com.gamenews.news.dto.EntityReviewDto;
import com.gamenews.news.service.EntityReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/entity-reviews")
@RequiredArgsConstructor
public class EntityReviewInternalController {

    private final EntityReviewService entityReviewService;

    @PostMapping("/resolve-game")
    public ResponseEntity<ApiResponse<EntityReviewDto.InternalResolveResponse>> resolveGame(
            @Valid @RequestBody EntityReviewDto.InternalResolveRequest request) {
        return ResponseEntity.ok(ApiResponse.success(entityReviewService.resolveGame(request)));
    }

    @PostMapping("/resolve-franchise")
    public ResponseEntity<ApiResponse<EntityReviewDto.InternalResolveResponse>> resolveFranchise(
            @Valid @RequestBody EntityReviewDto.InternalResolveRequest request) {
        return ResponseEntity.ok(ApiResponse.success(entityReviewService.resolveFranchise(request)));
    }
}
