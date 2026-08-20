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
@RequestMapping("/api/franchises")
@RequiredArgsConstructor
public class FranchiseController {

    private final FranchiseService franchiseService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<FranchiseDto.FranchiseResponse>>> getAllFranchises() {
        return ResponseEntity.ok(ApiResponse.success(franchiseService.getAllFranchises()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<FranchiseDto.FranchiseResponse>> getFranchise(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(franchiseService.getFranchise(id)));
    }
}
