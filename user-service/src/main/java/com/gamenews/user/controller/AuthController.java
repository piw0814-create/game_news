package com.gamenews.user.controller;

import com.gamenews.user.dto.AuthDto;
import com.gamenews.user.dto.UserDto;
import com.gamenews.user.service.AuthService;
import com.nimbusds.jose.jwk.RSAKey;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RSAKey rsaKey;

    @PostMapping("/login")
    public ResponseEntity<UserDto.ApiResponse<AuthDto.TokenResponse>> login(
            @Valid @RequestBody AuthDto.LoginRequest request) {
        try {
            AuthDto.TokenResponse response = authService.login(request);
            return ResponseEntity.ok(UserDto.ApiResponse.success(response));
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(UserDto.ApiResponse.<AuthDto.TokenResponse>builder()
                            .success(false)
                            .message(e.getMessage())
                            .build());
        }
    }

    @GetMapping("/jwks")
    public Map<String, Object> jwks() {
        return Map.of("keys", java.util.List.of(rsaKey.toPublicJWK().toJSONObject()));
    }
}
