package com.gamenews.news.service;

import com.gamenews.news.client.IgdbClient;
import com.gamenews.news.dto.GameDto;
import com.gamenews.news.dto.GameEnrichmentDto;
import com.gamenews.news.entity.Game;
import com.gamenews.news.entity.GameEnrichmentStatus;
import com.gamenews.news.repository.GameRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GameEnrichmentService {

    private static final int MAX_EXTERNAL_ALIAS_LENGTH = 100;
    private static final Set<String> BLOCKED_ALIAS_SUFFIXES = Set.of(
            ".exe", ".app", ".bat", ".cmd", ".msi", ".dmg");

    private final GameRepository gameRepository;
    private final GameIdentityService gameIdentityService;
    private final GameSimilarityService similarityService;
    private final IgdbClient igdbClient;
    private final FranchiseService franchiseService;

    public GameEnrichmentDto.PreviewResponse preview(Long gameId) {
        Game game = findGame(gameId);
        if (!igdbClient.isConfigured()) {
            return GameEnrichmentDto.PreviewResponse.builder()
                    .gameId(gameId)
                    .query(game.getName())
                    .configured(false)
                    .candidates(List.of())
                    .build();
        }

        List<IgdbClient.IgdbGame> results = igdbClient.searchGames(game.getName(), 5);
        if (results.isEmpty() && game.getDisplayName() != null && !game.getDisplayName().isBlank()) {
            results = igdbClient.searchGames(game.getDisplayName(), 5);
        }

        List<GameEnrichmentDto.Candidate> candidates = results.stream()
                .map(result -> toCandidate(game, result))
                .sorted((left, right) -> right.getMatchScore().compareTo(left.getMatchScore()))
                .toList();

        return GameEnrichmentDto.PreviewResponse.builder()
                .gameId(gameId)
                .query(game.getName())
                .configured(true)
                .candidates(candidates)
                .build();
    }

    @Transactional
    public GameDto.GameResponse apply(Long gameId, Long igdbId) {
        Game game = findGame(gameId);
        gameRepository.findByIgdbId(igdbId)
                .filter(existing -> !existing.getId().equals(gameId))
                .ifPresent(existing -> {
                    throw new IllegalArgumentException(
                            "해당 IGDB 게임은 이미 다른 Game에 연결되어 있습니다: #" + existing.getId());
                });

        return applyRawSnapshot(game, igdbClient.getGameById(igdbId));
    }

    @Transactional
    public GameDto.GameResponse applyRawSnapshot(Game game, IgdbClient.IgdbGame raw) {
        if (game == null || raw == null || raw.getId() == null || raw.getName() == null || raw.getName().isBlank()) {
            throw new IllegalArgumentException("IGDB 게임 id/name이 필요합니다");
        }

        gameRepository.findByIgdbId(raw.getId())
                .filter(existing -> !existing.getId().equals(game.getId()))
                .ifPresent(existing -> {
                    throw new IllegalArgumentException(
                            "해당 IGDB 게임은 이미 다른 Game에 연결되어 있습니다: #" + existing.getId());
                });

        GameEnrichmentDto.Candidate candidate = toCandidate(game, raw);
        String developer = candidate.getDeveloper();
        String publisher = candidate.getPublisher();
        String genre = join(candidate.getGenres());
        String platform = join(candidate.getPlatforms());
        String imageUrl = candidate.getImageUrl();

        GameEnrichmentStatus status = allPresent(
                preferExisting(game.getDeveloper(), developer),
                preferExisting(game.getPublisher(), publisher),
                preferExisting(game.getGenre(), genre),
                preferExisting(game.getPlatform(), platform),
                preferExisting(game.getImageUrl(), imageUrl))
                ? GameEnrichmentStatus.ENRICHED
                : GameEnrichmentStatus.PARTIAL;

        String oldCanonicalName = game.getName();
        game.applyIgdbSnapshot(
                raw.getId(),
                raw.getName(),
                developer,
                publisher,
                genre,
                platform,
                imageUrl,
                raw.getGameType() == null ? null : raw.getGameType().getType(),
                raw.getVersionParent(),
                status);

        if (oldCanonicalName != null
                && !oldCanonicalName.equalsIgnoreCase(game.getName())
                && !gameIdentityService.isIdentityUsedByAnotherGame(game.getId(), oldCanonicalName)) {
            game.addAlias(oldCanonicalName);
        }
        mergeAliases(game, candidate);
        franchiseService.syncIgdbFranchises(game, raw.getFranchise(), raw.getFranchises());
        gameRepository.save(game);
        return GameDto.GameResponse.from(game);
    }

    @Transactional
    public boolean autoApplyBestMatch(Long gameId) {
        Game game = findGame(gameId);
        if (!igdbClient.isConfigured()) return false;
        if (game.getIgdbId() != null) return true;

        List<IgdbClient.IgdbGame> results = new ArrayList<>(igdbClient.searchGames(game.getName(), 5));
        if (results.isEmpty() && game.getDisplayName() != null && !game.getDisplayName().isBlank()) {
            results.addAll(igdbClient.searchGames(game.getDisplayName(), 5));
        }
        if (results.isEmpty()) return false;

        List<GameEnrichmentDto.Candidate> candidates = results.stream()
                .map(result -> toCandidate(game, result))
                .sorted((left, right) -> right.getMatchScore().compareTo(left.getMatchScore()))
                .toList();
        GameEnrichmentDto.Candidate best = candidates.get(0);
        BigDecimal secondScore = candidates.size() > 1 ? candidates.get(1).getMatchScore() : BigDecimal.ZERO;
        List<IgdbClient.IgdbGame> exactIdentityMatches = results.stream()
                .filter(raw -> hasExactIdentityMatch(game, raw))
                .toList();
        boolean exactUnique = exactIdentityMatches.size() == 1
                && exactIdentityMatches.get(0).getId().equals(best.getIgdbId());
        boolean strongUnique = best.getMatchScore().compareTo(new BigDecimal("0.9500")) >= 0
                && best.getMatchScore().subtract(secondScore).compareTo(new BigDecimal("0.0500")) >= 0;
        if (!exactUnique && !strongUnique) return false;

        apply(gameId, best.getIgdbId());
        return true;
    }

    @Transactional
    public GameDto.GameResponse refresh(Long gameId) {
        Game game = findGame(gameId);
        if (game.getIgdbId() == null) {
            throw new IllegalArgumentException("IGDB ID가 연결되지 않은 게임입니다: " + gameId);
        }
        return apply(gameId, game.getIgdbId());
    }

    private void mergeAliases(Game game, GameEnrichmentDto.Candidate candidate) {
        List<String> suggestions = new ArrayList<>();
        suggestions.add(candidate.getName());
        suggestions.addAll(candidate.getAliases() == null ? List.of() : candidate.getAliases());
        if (candidate.getLocalizedNames() != null) {
            candidate.getLocalizedNames().forEach(localized -> suggestions.add(localized.getName()));
        }

        List<String> sanitizedSuggestions = sanitizeExternalAliases(suggestions);
        List<String> normalized = gameIdentityService.normalizeAliases(
                game.getName(), game.getDisplayName(), sanitizedSuggestions);
        if (normalized == null) {
            return;
        }

        for (String alias : normalized) {
            if (!gameIdentityService.isIdentityUsedByAnotherGame(game.getId(), alias)) {
                game.addAlias(alias);
            }
        }
    }

    private List<String> sanitizeExternalAliases(List<String> suggestions) {
        if (suggestions == null || suggestions.isEmpty()) {
            return List.of();
        }

        return suggestions.stream()
                .filter(this::isSafeExternalAlias)
                .map(String::trim)
                .toList();
    }

    private boolean isSafeExternalAlias(String value) {
        if (!hasText(value)) {
            return false;
        }

        String trimmed = value.trim();
        if (trimmed.length() >= MAX_EXTERNAL_ALIAS_LENGTH) {
            return false;
        }

        String normalized = trimmed.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("http://")
                || normalized.startsWith("https://")
                || normalized.startsWith("www.")) {
            return false;
        }

        return BLOCKED_ALIAS_SUFFIXES.stream()
                .noneMatch(normalized::endsWith);
    }

    private GameEnrichmentDto.Candidate toCandidate(Game game, IgdbClient.IgdbGame raw) {
        List<String> aliases = safe(raw.getAlternativeNames()).stream()
                .map(IgdbClient.IgdbAlternativeName::getName)
                .filter(this::hasText)
                .distinct()
                .toList();

        List<GameEnrichmentDto.LocalizedName> localizedNames = safe(raw.getGameLocalizations()).stream()
                .filter(localized -> hasText(localized.getName()))
                .map(localized -> GameEnrichmentDto.LocalizedName.builder()
                        .name(localized.getName())
                        .regionIdentifier(localized.getRegion() == null ? null : localized.getRegion().getIdentifier())
                        .regionName(localized.getRegion() == null ? null : localized.getRegion().getName())
                        .build())
                .toList();

        String developer = companyNames(raw, true);
        String publisher = companyNames(raw, false);
        List<String> genres = namedValues(raw.getGenres());
        List<String> platforms = namedValues(raw.getPlatforms());
        String imageUrl = normalizeCoverUrl(raw.getCover() == null ? null : raw.getCover().getUrl());

        List<String> currentIdentities = gameIdentities(game);
        List<String> externalIdentities = new ArrayList<>();
        externalIdentities.add(raw.getName());
        externalIdentities.addAll(aliases);
        localizedNames.forEach(localized -> externalIdentities.add(localized.getName()));

        GameSimilarityService.SimilarityResult similarity = similarityService.compare(
                currentIdentities,
                externalIdentities,
                game.getPublisher(),
                publisher,
                game.getDeveloper(),
                developer);

        return GameEnrichmentDto.Candidate.builder()
                .igdbId(raw.getId())
                .name(raw.getName())
                .matchScore(BigDecimal.valueOf(similarity.score()).setScale(4, RoundingMode.HALF_UP))
                .matchReasons(similarity.reasons())
                .developer(developer)
                .publisher(publisher)
                .genres(genres)
                .platforms(platforms)
                .aliases(aliases)
                .localizedNames(localizedNames)
                .primaryFranchise(raw.getFranchise() == null ? null : raw.getFranchise().getName())
                .franchises(franchiseNames(raw))
                .imageUrl(imageUrl)
                .build();
    }

    private String companyNames(IgdbClient.IgdbGame raw, boolean developer) {
        Set<String> names = new LinkedHashSet<>();
        for (IgdbClient.IgdbInvolvedCompany involved : safe(raw.getInvolvedCompanies())) {
            boolean matchesRole = developer ? involved.isDeveloper() : involved.isPublisher();
            if (!matchesRole || involved.getCompany() == null || !hasText(involved.getCompany().getName())) {
                continue;
            }
            names.add(involved.getCompany().getName().trim());
        }
        return names.isEmpty() ? null : String.join(", ", names);
    }

    private List<String> namedValues(List<IgdbClient.IgdbNamedEntity> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .map(IgdbClient.IgdbNamedEntity::getName)
                .filter(this::hasText)
                .map(String::trim)
                .distinct()
                .toList();
    }

    private List<String> gameIdentities(Game game) {
        List<String> identities = new ArrayList<>();
        identities.add(game.getName());
        identities.add(game.getDisplayName());
        game.getAliases().forEach(alias -> identities.add(alias.getAlias()));
        return identities;
    }

    private List<String> franchiseNames(IgdbClient.IgdbGame raw) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        if (raw.getFranchise() != null && hasText(raw.getFranchise().getName())) {
            names.add(raw.getFranchise().getName().trim());
        }
        for (IgdbClient.IgdbNamedEntity franchise : safe(raw.getFranchises())) {
            if (franchise != null && hasText(franchise.getName())) {
                names.add(franchise.getName().trim());
            }
        }
        return List.copyOf(names);
    }

    private boolean hasExactIdentityMatch(Game game, IgdbClient.IgdbGame raw) {
        if (raw == null) return false;
        Set<String> current = gameIdentities(game).stream()
                .filter(this::hasText)
                .map(this::identityKey)
                .collect(java.util.stream.Collectors.toSet());
        List<String> external = new ArrayList<>();
        external.add(raw.getName());
        safe(raw.getAlternativeNames()).forEach(value -> external.add(value.getName()));
        safe(raw.getGameLocalizations()).forEach(value -> external.add(value.getName()));
        return external.stream().filter(this::hasText).map(this::identityKey).anyMatch(current::contains);
    }

    private String identityKey(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]", "");
    }

    private String normalizeCoverUrl(String url) {
        if (!hasText(url)) {
            return null;
        }
        String normalized = url.trim();
        if (normalized.startsWith("//")) {
            normalized = "https:" + normalized;
        }
        return normalized.replace("/t_thumb/", "/t_cover_big/");
    }

    private String join(List<String> values) {
        return values == null || values.isEmpty() ? null : String.join(", ", values);
    }


    private String preferExisting(String existing, String candidate) {
        return hasText(existing) ? existing : candidate;
    }

    private boolean allPresent(String... values) {
        for (String value : values) {
            if (!hasText(value)) {
                return false;
            }
        }
        return true;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    private Game findGame(Long gameId) {
        return gameRepository.findById(gameId)
                .orElseThrow(() -> new IllegalArgumentException("게임을 찾을 수 없습니다: " + gameId));
    }
}
