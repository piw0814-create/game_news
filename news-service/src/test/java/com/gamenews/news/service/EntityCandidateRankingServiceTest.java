package com.gamenews.news.service;

import com.gamenews.news.dto.EntityReviewDto;
import com.gamenews.news.entity.EntityReviewKind;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EntityCandidateRankingServiceTest {

    private final EntityCandidateRankingService service = new EntityCandidateRankingService();

    @Test
    void theFinalsKeepsExactCandidateAndDropsFinalFantasyNoise() {
        List<EntityReviewDto.Candidate> ranked = service.rank(
                "The Finals",
                EntityReviewKind.GAME,
                List.of(
                        game(10L, "Final Fantasy VII", "Main Game"),
                        game(20L, "THE FINALS", "Main Game"),
                        game(30L, "Final Fantasy XVI", "Main Game")));

        assertThat(ranked).extracting(EntityReviewDto.Candidate::getName)
                .containsExactly("THE FINALS");
        assertThat(service.isSafeAutoEquivalent("The Finals", "Final Fantasy VII")).isFalse();
    }

    @Test
    void omochimKeepsCloseSpellingButDoesNotBecomeSafeAuto() {
        List<EntityReviewDto.Candidate> ranked = service.rank(
                "OmOchim",
                EntityReviewKind.GAME,
                List.of(
                        game(10L, "Omochi", "Main Game"),
                        game(20L, "Mochi Maker", "Main Game"),
                        game(30L, "Final Fantasy", "Main Game")));

        assertThat(ranked).extracting(EntityReviewDto.Candidate::getName)
                .contains("Omochi")
                .doesNotContain("Final Fantasy");
        assertThat(service.isSafeAutoEquivalent("OmOchim", "Omochi")).isFalse();
    }

    @Test
    void canonicalNormalizationHandlesPunctuationAccentAndNumberForms() {
        assertThat(service.isSafeAutoEquivalent("Grand Theft Auto 6", "Grand Theft Auto VI")).isTrue();
        assertThat(service.isSafeAutoEquivalent("Mortal Shell 2", "Mortal Shell II")).isTrue();
        assertThat(service.isSafeAutoEquivalent("Marvel’s Wolverine", "Marvel's Wolverine")).isTrue();
        assertThat(service.isSafeAutoEquivalent("Marvel Tōkon: Fighting Souls", "Marvel Tokon: Fighting Souls")).isTrue();
        assertThat(service.isSafeAutoEquivalent("Denshattack", "Denshattack!")).isTrue();
        assertThat(service.isSafeAutoEquivalent("Phantom Blade Zero", "Phantom Blade 0")).isTrue();
        assertThat(service.isSafeAutoEquivalent(
                "Tomb Raider I-III Remastered",
                "Tomb Raider I•II•III Remastered")).isTrue();
    }

    @Test
    void leadingExpansionAllowsCanonicalBrandPrefixButNotTrailingEditionNoise() {
        assertThat(service.isSafeAutoEquivalent("FC 26", "EA Sports FC 26")).isTrue();
        assertThat(service.isSafeAutoEquivalent("Modern Warfare 4", "Call of Duty: Modern Warfare 4")).isTrue();
        assertThat(service.isSafeAutoEquivalent("Panic Bomber W", "Super Bomberman: Panic Bomber W")).isTrue();
        assertThat(service.isSafeAutoEquivalent("A Plague Tale Legacy", "Resonance: A Plague Tale Legacy")).isTrue();
        assertThat(service.isSafeAutoEquivalent("FC 26", "FC 26 Quiz")).isFalse();
        assertThat(service.isSafeAutoEquivalent("FC 26", "EA Sports FC 26: Ultimate Edition")).isFalse();
    }

    @Test
    void standardEditionCanSafelyCollapseToBaseTitle() {
        assertThat(service.standardEditionBaseName("Train Sim World 6: Standard Edition"))
                .isEqualTo("Train Sim World 6");
        assertThat(service.isSafeAutoEquivalent(
                "Train Sim World 6: Standard Edition",
                "Train Sim World 6")).isTrue();
        assertThat(service.standardEditionBaseName("Train Sim World 6: Deluxe Edition")).isNull();
    }

    @Test
    void compositeExpansionNameCanCollapseToReviewOnlyLookupKey() {
        assertThat(service.collapsedQualifiedTailName(
                "S.T.A.L.K.E.R. 2: Heart of Chornobyl – Cost of Hope"))
                .isEqualTo("S.T.A.L.K.E.R. 2: Cost of Hope");
        assertThat(service.collapsedQualifiedTailName("A Plague Tale: Requiem")).isNull();
        assertThat(service.collapsedQualifiedTailName("Call of Duty – Black Ops")).isNull();
    }

    @Test
    void fc26PrefersCanonicalLeadingExpansionOverTrailingNoise() {
        List<EntityReviewDto.Candidate> ranked = service.rank(
                "FC 26",
                EntityReviewKind.GAME,
                List.of(
                        game(10L, "FC 26 Quiz", "Main Game"),
                        game(20L, "EA Sports FC 26", "Main Game"),
                        game(30L, "EA Sports FC 26: Ultimate Edition", "Main Game")));

        assertThat(ranked).extracting(EntityReviewDto.Candidate::getName)
                .contains("EA Sports FC 26", "FC 26 Quiz");
        assertThat(ranked.get(0).getName()).isEqualTo("EA Sports FC 26");
    }

    @Test
    void leadingArticleDifferenceStillRanksAsNearExact() {
        double score = service.identityScore("The Finals", "Finals");
        assertThat(score).isGreaterThanOrEqualTo(0.95);
    }

    @Test
    void preferredKindWinsWhenIdentityScoresAreEqual() {
        EntityReviewDto.Candidate franchise = EntityReviewDto.Candidate.builder()
                .source("IGDB")
                .entityKind(EntityReviewKind.FRANCHISE)
                .igdbId(100L)
                .name("Mass Effect")
                .build();
        EntityReviewDto.Candidate game = game(200L, "Mass Effect", "Main Game");

        List<EntityReviewDto.Candidate> ranked = service.rank(
                "Mass Effect",
                EntityReviewKind.FRANCHISE,
                List.of(game, franchise));

        assertThat(ranked.get(0).getEntityKind()).isEqualTo(EntityReviewKind.FRANCHISE);
    }

    private EntityReviewDto.Candidate game(Long igdbId, String name, String gameType) {
        return EntityReviewDto.Candidate.builder()
                .source("IGDB")
                .entityKind(EntityReviewKind.GAME)
                .igdbId(igdbId)
                .name(name)
                .gameType(gameType)
                .build();
    }
}
