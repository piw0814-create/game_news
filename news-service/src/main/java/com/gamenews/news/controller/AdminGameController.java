package com.gamenews.news.controller;

import com.gamenews.news.common.ApiResponse;
import com.gamenews.news.dto.GameDto;
import com.gamenews.news.dto.GameEnrichmentDto;
import com.gamenews.news.dto.GameReviewDto;
import com.gamenews.news.dto.GameReviewResolutionDto;
import com.gamenews.news.entity.GameReviewStatus;
import com.gamenews.news.service.GameAdminService;
import com.gamenews.news.service.GameEnrichmentService;
import com.gamenews.news.service.GameReviewService;
import com.gamenews.news.service.GameReviewResolutionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/games")
@RequiredArgsConstructor
public class AdminGameController {

    private final GameAdminService gameAdminService;
    private final GameEnrichmentService gameEnrichmentService;
    private final GameReviewService gameReviewService;
    private final GameReviewResolutionService gameReviewResolutionService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<GameDto.GameResponse>>> getGames(
            @RequestParam(required = false) GameReviewStatus reviewStatus) {
        return ResponseEntity.ok(ApiResponse.success(gameAdminService.getGames(reviewStatus)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<GameDto.GameResponse>> getGame(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(gameAdminService.getGame(id)));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<GameDto.GameResponse>> updateGame(
            @PathVariable Long id,
            @Valid @RequestBody GameDto.AdminUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(gameAdminService.updateGame(id, request)));
    }

    @PostMapping("/{id}/confirm")
    public ResponseEntity<ApiResponse<GameDto.GameResponse>> confirmGame(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(gameAdminService.confirmGame(id)));
    }

    @PostMapping("/{id}/merge")
    public ResponseEntity<ApiResponse<GameDto.GameResponse>> mergeGame(
            @PathVariable Long id,
            @Valid @RequestBody GameDto.MergeRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                gameAdminService.mergeGame(id, request.getTargetGameId())));
    }

    @GetMapping("/{id}/review-context")
    public ResponseEntity<ApiResponse<GameReviewDto.ReviewContextResponse>> getReviewContext(
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(gameReviewService.getReviewContext(id)));
    }

    @PostMapping("/{id}/enrichment/preview")
    public ResponseEntity<ApiResponse<GameEnrichmentDto.PreviewResponse>> previewEnrichment(
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(gameEnrichmentService.preview(id)));
    }

    @PostMapping("/{id}/enrichment/apply")
    public ResponseEntity<ApiResponse<GameDto.GameResponse>> applyEnrichment(
            @PathVariable Long id,
            @Valid @RequestBody GameEnrichmentDto.ApplyRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                gameEnrichmentService.apply(id, request.getIgdbId())));
    }

    @PostMapping("/{id}/resolve-franchise")
    public ResponseEntity<ApiResponse<GameReviewResolutionDto.ResolveAsFranchiseResponse>> resolveAsFranchise(
            @PathVariable Long id,
            @Valid @RequestBody GameReviewResolutionDto.ResolveAsFranchiseRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                gameReviewResolutionService.resolveAsFranchise(id, request.getFranchiseId())));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<Void>> rejectGame(@PathVariable Long id) {
        gameAdminService.rejectGame(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}

