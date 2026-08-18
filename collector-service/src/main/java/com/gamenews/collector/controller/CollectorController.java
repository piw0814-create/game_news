package com.gamenews.collector.controller;

import com.gamenews.collector.dto.CollectorDto;
import com.gamenews.collector.service.CollectorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/collector")
@RequiredArgsConstructor
public class CollectorController {

    private final CollectorService collectorService;

    @PostMapping("/all")
    public ResponseEntity<CollectorDto.ApiResponse<List<CollectorDto.CollectionResult>>> collectAll(
            @RequestParam(defaultValue = "1") int limit) {

        List<CollectorDto.CollectionResult> results = collectorService.collectAll(limit);

        return ResponseEntity.ok(
                CollectorDto.ApiResponse.success("전체 수집 완료", results));
    }

    @PostMapping("/{sourceKey}")
    public ResponseEntity<CollectorDto.ApiResponse<CollectorDto.CollectionResult>> collectSource(
            @PathVariable String sourceKey,
            @RequestParam(defaultValue = "1") int limit) {

        CollectorDto.CollectionResult result = collectorService.collect(sourceKey, limit);

        return ResponseEntity.ok(
                CollectorDto.ApiResponse.success("수집 완료", result));
    }
}
