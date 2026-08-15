package com.gamenews.news.controller;

import com.gamenews.news.common.ApiResponse;
import com.gamenews.news.dto.GameDto;
import com.gamenews.news.service.GameService;
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
@RequestMapping("/api/games")
@RequiredArgsConstructor
public class GameController {

    private final GameService gameService;

    @PostMapping
    public ResponseEntity<ApiResponse<GameDto.GameResponse>> createGame(
            @Valid @RequestBody GameDto.CreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(gameService.createGame(request)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<GameDto.GameResponse>>> getAllGames() {
        return ResponseEntity.ok(ApiResponse.success(gameService.getAllGames()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<GameDto.GameResponse>> getGame(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(gameService.getGame(id)));
    }
}
