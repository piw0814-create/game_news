package com.gamenews.news.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GameSimilarityServiceTest {

    private final GameSimilarityService service = new GameSimilarityService();

    @Test
    void shortAliasDoesNotCreateContainsFalsePositive() {
        GameSimilarityService.SimilarityResult result = service.compare(
                List.of("Neverness To Everness", "이환", "NTE"),
                List.of("Resident Evil Requiem"),
                null,
                null,
                null,
                null);

        assertThat(result.score()).isLessThan(0.35);
        assertThat(result.identityScore()).isLessThan(0.55);
        assertThat(result.duplicateCandidate()).isFalse();
        assertThat(result.reasons()).isEmpty();
    }

    @Test
    void shortAliasExactMatchIsStillStrong() {
        GameSimilarityService.SimilarityResult result = service.compare(
                List.of("NTE"),
                List.of("NTE"),
                null,
                null,
                null,
                null);

        assertThat(result.score()).isEqualTo(0.93);
        assertThat(result.identityScore()).isEqualTo(0.93);
        assertThat(result.duplicateCandidate()).isTrue();
        assertThat(result.reasons()).anyMatch(reason -> reason.contains("정확 일치"));
    }

    @Test
    void shortAliasesDoNotUseFuzzyMatching() {
        GameSimilarityService.SimilarityResult result = service.compare(
                List.of("NTE"),
                List.of("NTF"),
                null,
                null,
                null,
                null);

        assertThat(result.score()).isZero();
        assertThat(result.identityScore()).isZero();
        assertThat(result.duplicateCandidate()).isFalse();
        assertThat(result.reasons()).isEmpty();
    }

    @Test
    void longRelatedNamesRemainStrongCandidates() {
        GameSimilarityService.SimilarityResult result = service.compare(
                List.of("Neverness To Everness"),
                List.of("NTE: Neverness to Everness"),
                null,
                null,
                null,
                null);

        assertThat(result.score()).isGreaterThanOrEqualTo(0.72);
        assertThat(result.identityScore()).isGreaterThanOrEqualTo(0.55);
        assertThat(result.duplicateCandidate()).isTrue();
        assertThat(result.reasons()).anyMatch(reason ->
                reason.contains("Neverness To Everness")
                        && reason.contains("NTE: Neverness to Everness"));
    }

    @Test
    void metadataMatchCannotPromoteWeakIdentityIntoDuplicateCandidate() {
        GameSimilarityService.SimilarityResult result = service.compare(
                List.of("Neverness To Everness"),
                List.of("Marvel's Wolverine"),
                "Same Publisher",
                "Same Publisher",
                "Same Developer",
                "Same Developer");

        assertThat(result.identityScore()).isLessThan(0.55);
        assertThat(result.score()).isGreaterThan(result.identityScore());
        assertThat(result.duplicateCandidate()).isFalse();
    }
}
