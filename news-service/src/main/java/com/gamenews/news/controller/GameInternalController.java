package com.gamenews.news.controller;

import com.gamenews.news.common.ApiResponse;
import com.gamenews.news.dto.GameDto;
import com.gamenews.news.service.GameService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/games")
@RequiredArgsConstructor
public class GameInternalController {

    private final GameService gameService;

    @PostMapping("/resolve-or-create")
    public ResponseEntity<ApiResponse<GameDto.ResolveOrCreateResponse>> resolveOrCreateAiGame(
            @Valid @RequestBody GameDto.ResolveOrCreateAiRequest request) {
        return ResponseEntity.ok(ApiResponse.success(gameService.resolveOrCreateAiGame(request)));
    }
}
