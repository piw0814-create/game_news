package com.gamenews.news.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class ArticleContentMaintenanceDto {

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SanitizeResponse {
        private boolean dryRun;
        private int candidates;
        private int changed;
        private int unchanged;
        private int updated;
        private long totalCharsBefore;
        private long totalCharsAfter;
        private double reductionPercent;
    }
}
