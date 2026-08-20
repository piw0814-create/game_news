package com.gamenews.news.controller;

import com.gamenews.news.common.ApiResponse;
import com.gamenews.news.dto.FranchiseAdminDto;
import com.gamenews.news.service.FranchiseAdminService;
import com.gamenews.news.service.FranchiseCatalogSyncService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/admin/franchises")
@RequiredArgsConstructor
public class AdminFranchiseController {

    private final FranchiseAdminService franchiseAdminService;
    private final FranchiseCatalogSyncService franchiseCatalogSyncService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<FranchiseAdminDto.SummaryResponse>>> getFranchises(
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(ApiResponse.success(franchiseAdminService.getFranchises(search)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<FranchiseAdminDto.DetailResponse>> getFranchise(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(franchiseAdminService.getFranchise(id)));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<FranchiseAdminDto.DetailResponse>> updateFranchise(
            @PathVariable Long id,
            @Valid @RequestBody FranchiseAdminDto.UpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(franchiseAdminService.updateFranchise(id, request)));
    }

    @PostMapping("/{id}/sync-igdb")
    public ResponseEntity<ApiResponse<FranchiseAdminDto.SyncResponse>> syncIgdb(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(franchiseCatalogSyncService.sync(id)));
    }

    @PostMapping("/{id}/merge")
    public ResponseEntity<ApiResponse<FranchiseAdminDto.DetailResponse>> mergeFranchise(
            @PathVariable Long id,
            @Valid @RequestBody FranchiseAdminDto.MergeRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                franchiseAdminService.mergeFranchise(id, request.getTargetFranchiseId())));
    }

    @PostMapping("/{id}/games")
    public ResponseEntity<ApiResponse<FranchiseAdminDto.DetailResponse>> linkGame(
            @PathVariable Long id,
            @Valid @RequestBody FranchiseAdminDto.GameLinkRequest request) {
        return ResponseEntity.ok(ApiResponse.success(franchiseAdminService.linkGame(id, request)));
    }

    @PatchMapping("/{id}/games/{gameId}")
    public ResponseEntity<ApiResponse<FranchiseAdminDto.DetailResponse>> updateGameLink(
            @PathVariable Long id,
            @PathVariable Long gameId,
            @Valid @RequestBody FranchiseAdminDto.GameLinkUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(franchiseAdminService.updateGameLink(id, gameId, request)));
    }

    @DeleteMapping("/{id}/games/{gameId}")
    public ResponseEntity<ApiResponse<FranchiseAdminDto.DetailResponse>> unlinkGame(
            @PathVariable Long id,
            @PathVariable Long gameId) {
        return ResponseEntity.ok(ApiResponse.success(franchiseAdminService.unlinkGame(id, gameId)));
    }
}
