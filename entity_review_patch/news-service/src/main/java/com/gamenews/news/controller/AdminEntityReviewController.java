package com.gamenews.news.controller;

import com.gamenews.news.common.ApiResponse;
import com.gamenews.news.dto.EntityReviewDto;
import com.gamenews.news.entity.EntityReviewStatus;
import com.gamenews.news.service.EntityReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/entity-reviews")
@RequiredArgsConstructor
public class AdminEntityReviewController {

    private final EntityReviewService entityReviewService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<EntityReviewDto.AdminResponse>>> getReviews(
            @RequestParam(required = false) EntityReviewStatus status) {
        return ResponseEntity.ok(ApiResponse.success(entityReviewService.getAdminReviews(status)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<EntityReviewDto.AdminResponse>> getReview(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(entityReviewService.getAdminReview(id)));
    }

    @PostMapping("/{id}/resolve")
    public ResponseEntity<ApiResponse<EntityReviewDto.AdminResponse>> resolve(
            @PathVariable Long id,
            @Valid @RequestBody EntityReviewDto.AdminResolveRequest request) {
        return ResponseEntity.ok(ApiResponse.success(entityReviewService.resolveAdmin(id, request)));
    }
}
