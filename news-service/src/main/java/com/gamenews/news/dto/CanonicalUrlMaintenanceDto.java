package com.gamenews.news.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

public class CanonicalUrlMaintenanceDto {

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ArticleRef {
        private Long id;
        private String url;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ConflictGroup {
        private String canonicalUrl;
        private List<ArticleRef> articles;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class BackfillResponse {
        private boolean dryRun;
        private int candidates;
        private int assignable;
        private int updated;
        private int conflictGroups;
        private int conflictedArticles;
        private List<ConflictGroup> conflicts;
    }
}
