package com.gamenews.news.controller;

import com.gamenews.news.common.ApiResponse;
import com.gamenews.news.dto.FranchiseDto;
import com.gamenews.news.service.FranchiseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/games")
@RequiredArgsConstructor
public class GameFranchiseController {

    private final FranchiseService franchiseService;

    @GetMapping("/{gameId}/franchises")
    public ResponseEntity<ApiResponse<List<FranchiseDto.GameFranchiseResponse>>> getFranchisesByGame(
            @PathVariable Long gameId) {
        return ResponseEntity.ok(ApiResponse.success(franchiseService.getFranchisesByGame(gameId)));
    }

    @GetMapping("/franchise-ids")
    public ResponseEntity<ApiResponse<List<Long>>> getFranchiseIdsByGames(
            @RequestParam List<Long> gameIds) {
        return ResponseEntity.ok(ApiResponse.success(franchiseService.getFranchiseIdsByGames(gameIds)));
    }
}
