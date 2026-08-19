package com.gamenews.interest.controller;

import com.gamenews.interest.dto.InterestDto;
import com.gamenews.interest.service.InterestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/interests/games")
@RequiredArgsConstructor
public class InterestInternalController {

    private final InterestService interestService;

    @PostMapping("/merge")
    public ResponseEntity<InterestDto.ApiResponse<Void>> mergeGameReferences(
            @RequestParam Long sourceGameId,
            @RequestParam Long targetGameId) {
        interestService.mergeGameReferences(sourceGameId, targetGameId);
        return ResponseEntity.ok(InterestDto.ApiResponse.success(null));
    }

    @DeleteMapping("/{gameId}")
    public ResponseEntity<InterestDto.ApiResponse<Void>> deleteGameReferences(@PathVariable Long gameId) {
        interestService.deleteGameReferences(gameId);
        return ResponseEntity.ok(InterestDto.ApiResponse.success(null));
    }
}
