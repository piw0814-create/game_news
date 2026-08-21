package com.gamenews.news.controller;

import com.gamenews.news.dto.ArticleContentMaintenanceDto;
import com.gamenews.news.dto.CanonicalUrlMaintenanceDto;
import com.gamenews.news.service.ArticleContentMaintenanceService;
import com.gamenews.news.service.CanonicalUrlMaintenanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/news")
@RequiredArgsConstructor
public class AdminNewsMaintenanceController {

    private final CanonicalUrlMaintenanceService canonicalUrlMaintenanceService;
    private final ArticleContentMaintenanceService articleContentMaintenanceService;


    @PostMapping("/content/sanitize")
    public ResponseEntity<ArticleContentMaintenanceDto.SanitizeResponse> sanitizeArticleContent(
            @RequestParam(defaultValue = "true") boolean dryRun) {
        return ResponseEntity.ok(articleContentMaintenanceService.sanitize(dryRun));
    }

    @PostMapping("/canonical-urls/backfill")
    public ResponseEntity<CanonicalUrlMaintenanceDto.BackfillResponse> backfillCanonicalUrls(
            @RequestParam(defaultValue = "true") boolean dryRun) {
        return ResponseEntity.ok(canonicalUrlMaintenanceService.backfill(dryRun));
    }
}
