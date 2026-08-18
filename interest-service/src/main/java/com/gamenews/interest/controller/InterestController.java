package com.gamenews.interest.controller;

import com.gamenews.interest.dto.InterestDto;
import com.gamenews.interest.service.InterestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/interests")
@RequiredArgsConstructor
public class InterestController {

    private final InterestService interestService;

    @PostMapping("/games/{gameId}")
    public ResponseEntity<InterestDto.ApiResponse<InterestDto.InterestResponse>> addGame(
            @PathVariable Long gameId,
            @RequestHeader("X-User-Id") Long userId) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(InterestDto.ApiResponse.success(interestService.addGame(userId, gameId)));
    }

    @GetMapping("/games")
    public ResponseEntity<InterestDto.ApiResponse<List<InterestDto.InterestResponse>>> getMyGames(
            @RequestHeader("X-User-Id") Long userId) {

        return ResponseEntity.ok(
                InterestDto.ApiResponse.success(interestService.getMyGames(userId))
        );
    }

    @GetMapping("/game-ids")
    public ResponseEntity<InterestDto.ApiResponse<List<Long>>> getMyGameIds(
            @RequestHeader("X-User-Id") Long userId) {

        return ResponseEntity.ok(
                InterestDto.ApiResponse.success(interestService.getMyGameIds(userId))
        );
    }

    @DeleteMapping("/games/{gameId}")
    public ResponseEntity<Void> removeGame(
            @PathVariable Long gameId,
            @RequestHeader("X-User-Id") Long userId) {

        interestService.removeGame(userId, gameId);
        return ResponseEntity.noContent().build();
    }
}
