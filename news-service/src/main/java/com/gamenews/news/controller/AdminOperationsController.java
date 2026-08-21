package com.gamenews.news.controller;

import com.gamenews.news.dto.OperationalStatusDto;
import com.gamenews.news.service.OperationalStatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/operations")
@RequiredArgsConstructor
public class AdminOperationsController {

    private final OperationalStatusService operationalStatusService;

    @GetMapping("/news")
    public ResponseEntity<OperationalStatusDto.Response> getNewsStatus() {
        return ResponseEntity.ok(operationalStatusService.getStatus());
    }
}
