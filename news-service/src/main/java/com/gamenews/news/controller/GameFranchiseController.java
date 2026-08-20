package com.gamenews.news.controller;

import com.gamenews.news.common.ApiResponse;
import com.gamenews.news.dto.FranchiseDto;
import com.gamenews.news.service.FranchiseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/games/{gameId}/franchises")
@RequiredArgsConstructor
public class GameFranchiseController {

    private final FranchiseService franchiseService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<FranchiseDto.GameFranchiseResponse>>> getFranchisesByGame(
            @PathVariable Long gameId) {
        return ResponseEntity.ok(ApiResponse.success(franchiseService.getFranchisesByGame(gameId)));
    }
}
