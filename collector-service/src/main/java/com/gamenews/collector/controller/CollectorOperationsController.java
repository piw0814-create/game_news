package com.gamenews.collector.controller;

import com.gamenews.collector.dto.CollectorDto;
import com.gamenews.collector.service.CollectorOperationalService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/operations")
@RequiredArgsConstructor
public class CollectorOperationsController {

    private final CollectorOperationalService operationalService;

    @GetMapping("/collector")
    public ResponseEntity<CollectorDto.OperationalStatus> getCollectorStatus() {
        return ResponseEntity.ok(operationalService.getStatus());
    }
}
