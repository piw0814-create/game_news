package com.gamenews.news.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

public class GameEnrichmentDto {

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PreviewResponse {
        private Long gameId;
        private String query;
        private boolean configured;
        private List<Candidate> candidates;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Candidate {
        private Long igdbId;
        private String name;
        private BigDecimal matchScore;
        private List<String> matchReasons;
        private String developer;
        private String publisher;
        private List<String> genres;
        private List<String> platforms;
        private List<String> aliases;
        private List<LocalizedName> localizedNames;
        private String primaryFranchise;
        private List<String> franchises;
        private String imageUrl;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LocalizedName {
        private String name;
        private String regionIdentifier;
        private String regionName;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ApplyRequest {
        @NotNull(message = "IGDB 게임 ID는 필수입니다")
        private Long igdbId;
    }
}
