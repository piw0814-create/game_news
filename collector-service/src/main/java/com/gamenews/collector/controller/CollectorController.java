package com.gamenews.collector.controller;

import com.gamenews.collector.dto.CollectorDto;
import com.gamenews.collector.service.CollectorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/collector")
@RequiredArgsConstructor
public class CollectorController {

    private final CollectorService collectorService;

    @PostMapping("/pcgamer")
    public ResponseEntity<CollectorDto.ApiResponse<CollectorDto.CollectionResult>> collectPcGamer(
            @RequestParam(defaultValue = "1") int limit) {

        CollectorDto.CollectionResult result = collectorService.collectPcGamer(limit);

        return ResponseEntity.ok(
                CollectorDto.ApiResponse.success("수집 완료", result));
    }

    @PostMapping("/destructoid")
    public ResponseEntity<CollectorDto.ApiResponse<CollectorDto.CollectionResult>> collectDestructoid(
            @RequestParam(defaultValue = "1") int limit) {

        CollectorDto.CollectionResult result = collectorService.collectDestructoid(limit);

        return ResponseEntity.ok(
                CollectorDto.ApiResponse.success("수집 완료", result));
    }
}
