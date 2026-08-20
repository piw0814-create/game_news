package com.gamenews.news.service;

import com.gamenews.news.dto.FranchiseAdminDto;
import com.gamenews.news.entity.ArticleFranchise;
import com.gamenews.news.entity.Franchise;
import com.gamenews.news.entity.FranchiseAlias;
import com.gamenews.news.entity.FranchiseMetadataSource;
import com.gamenews.news.entity.Game;
import com.gamenews.news.entity.GameFranchise;
import com.gamenews.news.entity.GameFranchiseSource;
import com.gamenews.news.entity.TopicFranchise;
import com.gamenews.news.repository.ArticleFranchiseRepository;
import com.gamenews.news.repository.FranchiseRepository;
import com.gamenews.news.repository.GameFranchiseRepository;
import com.gamenews.news.repository.GameRepository;
import com.gamenews.news.repository.TopicFranchiseRepository;
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
public class FranchiseAdminService {

    private final FranchiseRepository franchiseRepository;
    private final GameFranchiseRepository gameFranchiseRepository;
    private final GameRepository gameRepository;
    private final ArticleFranchiseRepository articleFranchiseRepository;
    private final TopicFranchiseRepository topicFranchiseRepository;
    private final GameSimilarityService similarityService;

    public List<FranchiseAdminDto.SummaryResponse> getFranchises(String search) {
        String keyword = normalizeSearch(search);
        return franchiseRepository.findAllByOrderByNameAsc().stream()
                .filter(franchise -> keyword == null || searchableText(franchise).contains(keyword))
                .map(franchise -> FranchiseAdminDto.SummaryResponse.from(
                        franchise,
                        Math.toIntExact(gameFranchiseRepository.countByFranchise_Id(franchise.getId())),
                        Math.toIntExact(articleFranchiseRepository.countByFranchise_Id(franchise.getId())),
                        Math.toIntExact(topicFranchiseRepository.countByFranchise_Id(franchise.getId()))))
                .toList();
    }

    public FranchiseAdminDto.DetailResponse getFranchise(Long franchiseId) {
        return toDetail(findFranchise(franchiseId));
    }

    @Transactional
    public FranchiseAdminDto.DetailResponse createFranchise(FranchiseAdminDto.CreateRequest request) {
        String name = normalizeRequiredName(request.getName());
        String displayName = normalizeOptional(request.getDisplayName());
        List<String> aliases = normalizeAliases(name, displayName, request.getAliases());
        validateIdentityAvailable(null, name, displayName, aliases);

        Franchise franchise = Franchise.builder()
                .name(name)
                .displayName(displayName)
                .metadataSource(FranchiseMetadataSource.MANUAL)
                .build();
        aliases.forEach(franchise::addAlias);
        franchiseRepository.save(franchise);
        return toDetail(franchise);
    }

    @Transactional
    public FranchiseAdminDto.DetailResponse updateFranchise(
            Long franchiseId,
            FranchiseAdminDto.UpdateRequest request) {
        Franchise franchise = findFranchise(franchiseId);

        String name = request.getName() == null
                ? franchise.getName()
                : normalizeRequiredName(request.getName());
        boolean displayChanged = request.getDisplayName() != null;
        String displayName = displayChanged
                ? normalizeOptional(request.getDisplayName())
                : normalizeOptional(franchise.getDisplayName());
        List<String> currentAliases = franchise.getAliases().stream().map(FranchiseAlias::getAlias).toList();
        List<String> aliases = request.getAliases() == null
                ? normalizeAliases(name, displayName, currentAliases)
                : normalizeAliases(name, displayName, request.getAliases());

        validateIdentityAvailable(franchiseId, name, displayName, aliases);
        franchise.updateIdentity(name, displayName == null ? "" : displayName);
        if (displayChanged && displayName == null) franchise.clearDisplayName();
        franchise.replaceAliases(aliases);
        return toDetail(franchise);
    }

    @Transactional
    public FranchiseAdminDto.DetailResponse linkGame(
            Long franchiseId,
            FranchiseAdminDto.GameLinkRequest request) {
        Franchise franchise = findFranchise(franchiseId);
        Game game = findGame(request.getGameId());

        GameFranchise link = gameFranchiseRepository.findByGame_IdAndFranchise_Id(game.getId(), franchiseId)
                .orElseGet(() -> GameFranchise.builder()
                        .game(game)
                        .franchise(franchise)
                        .primary(false)
                        .source(GameFranchiseSource.MANUAL)
                        .build());

        applyPrimary(game.getId(), link, request.isPrimary());
        gameFranchiseRepository.save(link);
        return toDetail(franchise);
    }

    @Transactional
    public FranchiseAdminDto.DetailResponse updateGameLink(
            Long franchiseId,
            Long gameId,
            FranchiseAdminDto.GameLinkUpdateRequest request) {
        Franchise franchise = findFranchise(franchiseId);
        GameFranchise link = gameFranchiseRepository.findByGame_IdAndFranchise_Id(gameId, franchiseId)
                .orElseThrow(() -> new IllegalArgumentException("게임-프랜차이즈 연결을 찾을 수 없습니다"));
        applyPrimary(gameId, link, request.isPrimary());
        return toDetail(franchise);
    }

    @Transactional
    public FranchiseAdminDto.DetailResponse unlinkGame(Long franchiseId, Long gameId) {
        Franchise franchise = findFranchise(franchiseId);
        GameFranchise link = gameFranchiseRepository.findByGame_IdAndFranchise_Id(gameId, franchiseId)
                .orElseThrow(() -> new IllegalArgumentException("게임-프랜차이즈 연결을 찾을 수 없습니다"));
        gameFranchiseRepository.delete(link);
        gameFranchiseRepository.flush();
        return toDetail(franchise);
    }

    @Transactional
    public FranchiseAdminDto.DetailResponse mergeFranchise(Long sourceId, Long targetId) {
        if (sourceId.equals(targetId)) throw new IllegalArgumentException("같은 프랜차이즈로 병합할 수 없습니다");
        Franchise source = findFranchise(sourceId);
        Franchise target = findFranchise(targetId);

        if (source.getIgdbId() != null && target.getIgdbId() != null
                && !source.getIgdbId().equals(target.getIgdbId())) {
            throw new IllegalArgumentException("서로 다른 IGDB ID가 연결된 프랜차이즈는 병합할 수 없습니다");
        }

        List<String> sourceIdentities = new ArrayList<>();
        sourceIdentities.add(source.getName());
        sourceIdentities.add(source.getDisplayName());
        source.getAliases().forEach(alias -> sourceIdentities.add(alias.getAlias()));
        Long inheritedIgdbId = target.getIgdbId() == null ? source.getIgdbId() : null;
        String inheritedIgdbName = inheritedIgdbId == null ? null : source.getName();

        mergeGameLinks(source, target);
        mergeArticleLinks(source, target);
        mergeTopicLinks(source, target);

        source.clearAliases();
        franchiseRepository.flush();
        franchiseRepository.delete(source);
        franchiseRepository.flush();

        if (inheritedIgdbId != null) target.applyIgdbIdentity(inheritedIgdbId, inheritedIgdbName);
        for (String identity : sourceIdentities) {
            String alias = normalizeOptional(identity);
            if (alias == null || identityUsedByOther(target.getId(), alias)) continue;
            target.addAlias(alias);
        }
        franchiseRepository.save(target);
        return toDetail(target);
    }

    private void mergeGameLinks(Franchise source, Franchise target) {
        for (GameFranchise sourceLink : gameFranchiseRepository
                .findAllByFranchise_IdOrderByPrimaryDescCreatedAtAsc(source.getId())) {
            gameFranchiseRepository.findByGame_IdAndFranchise_Id(sourceLink.getGame().getId(), target.getId())
                    .ifPresentOrElse(targetLink -> {
                        targetLink.absorbMetadataFrom(sourceLink);
                        gameFranchiseRepository.delete(sourceLink);
                    }, () -> sourceLink.reassignFranchise(target));
        }
    }

    private void mergeArticleLinks(Franchise source, Franchise target) {
        for (ArticleFranchise sourceLink : articleFranchiseRepository.findAllByFranchise_IdOrderByIdAsc(source.getId())) {
            articleFranchiseRepository.findByArticle_IdAndFranchise_Id(sourceLink.getArticle().getId(), target.getId())
                    .ifPresentOrElse(targetLink -> {
                        targetLink.absorbMetadata(
                                sourceLink.isPrimary(), sourceLink.getConfidenceScore(), sourceLink.getRelevanceReason());
                        articleFranchiseRepository.delete(sourceLink);
                    }, () -> sourceLink.reassignFranchise(target));
        }
    }

    private void mergeTopicLinks(Franchise source, Franchise target) {
        for (TopicFranchise sourceLink : topicFranchiseRepository.findAllByFranchise_IdOrderByIdAsc(source.getId())) {
            topicFranchiseRepository.findByTopic_IdAndFranchise_Id(sourceLink.getTopic().getId(), target.getId())
                    .ifPresentOrElse(targetLink -> {
                        targetLink.absorbMetadata(sourceLink.isPrimary(), sourceLink.getRelevanceScore());
                        topicFranchiseRepository.delete(sourceLink);
                    }, () -> sourceLink.reassignFranchise(target));
        }
    }

    private void applyPrimary(Long gameId, GameFranchise link, boolean primary) {
        if (primary) {
            gameFranchiseRepository.findAllByGame_IdOrderByPrimaryDescCreatedAtAsc(gameId)
                    .forEach(existing -> existing.updatePrimary(false));
        }
        link.updatePrimary(primary);
    }

    private FranchiseAdminDto.DetailResponse toDetail(Franchise franchise) {
        List<GameFranchise> gameLinks = gameFranchiseRepository
                .findAllByFranchise_IdOrderByPrimaryDescCreatedAtAsc(franchise.getId());
        List<ArticleFranchise> articleLinks = articleFranchiseRepository
                .findAllByFranchise_IdOrderByIdAsc(franchise.getId());
        List<TopicFranchise> topicLinks = topicFranchiseRepository
                .findAllByFranchise_IdOrderByIdAsc(franchise.getId());
        return FranchiseAdminDto.DetailResponse.from(
                franchise, gameLinks, articleLinks, topicLinks, similarFranchises(franchise));
    }

    private List<FranchiseAdminDto.SimilarFranchiseResponse> similarFranchises(Franchise franchise) {
        List<String> current = identities(franchise);
        return franchiseRepository.findAll().stream()
                .filter(candidate -> !candidate.getId().equals(franchise.getId()))
                .map(candidate -> {
                    GameSimilarityService.SimilarityResult result = similarityService.compare(
                            current, identities(candidate), null, null, null, null);
                    return FranchiseAdminDto.SimilarFranchiseResponse.builder()
                            .id(candidate.getId())
                            .name(candidate.getName())
                            .displayName(candidate.getDisplayName())
                            .igdbId(candidate.getIgdbId())
                            .similarityScore(BigDecimal.valueOf(result.score()).setScale(4, RoundingMode.HALF_UP))
                            .reasons(result.reasons())
                            .build();
                })
                .filter(candidate -> candidate.getSimilarityScore().compareTo(new BigDecimal("0.5500")) >= 0)
                .sorted((a, b) -> b.getSimilarityScore().compareTo(a.getSimilarityScore()))
                .limit(5)
                .toList();
    }

    private void validateIdentityAvailable(Long excludeId, String name, String displayName, List<String> aliases) {
        Set<String> desired = new LinkedHashSet<>();
        desired.add(identityKey(name));
        if (displayName != null) desired.add(identityKey(displayName));
        aliases.forEach(alias -> desired.add(identityKey(alias)));

        for (Franchise candidate : franchiseRepository.findAll()) {
            if (excludeId != null && candidate.getId().equals(excludeId)) continue;
            for (String identity : identities(candidate)) {
                if (desired.contains(identityKey(identity))) {
                    throw new IllegalArgumentException(
                            "이미 같은 이름/표시명/별칭을 사용하는 프랜차이즈가 있습니다: " + candidate.getName());
                }
            }
        }
    }

    private boolean identityUsedByOther(Long excludeId, String value) {
        String key = identityKey(value);
        return franchiseRepository.findAll().stream()
                .filter(candidate -> !candidate.getId().equals(excludeId))
                .flatMap(candidate -> identities(candidate).stream())
                .map(this::identityKey)
                .anyMatch(key::equals);
    }

    private List<String> identities(Franchise franchise) {
        List<String> values = new ArrayList<>();
        values.add(franchise.getName());
        values.add(franchise.getDisplayName());
        franchise.getAliases().forEach(alias -> values.add(alias.getAlias()));
        return values.stream().filter(value -> value != null && !value.isBlank()).toList();
    }

    private List<String> normalizeAliases(String name, String displayName, List<String> aliases) {
        if (aliases == null) return List.of();
        Set<String> seen = new LinkedHashSet<>();
        List<String> result = new ArrayList<>();
        String nameKey = identityKey(name);
        String displayKey = displayName == null ? null : identityKey(displayName);

        for (String alias : aliases) {
            String normalized = normalizeOptional(alias);
            if (normalized == null) continue;
            String key = identityKey(normalized);
            if (key.equals(nameKey) || key.equals(displayKey) || !seen.add(key)) continue;
            result.add(normalized);
        }
        return result;
    }

    private String searchableText(Franchise franchise) {
        List<String> values = new ArrayList<>(identities(franchise));
        if (franchise.getIgdbId() != null) values.add(String.valueOf(franchise.getIgdbId()));
        return String.join(" ", values).toLowerCase(Locale.ROOT);
    }

    private String normalizeSearch(String value) {
        String normalized = normalizeOptional(value);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private String normalizeRequiredName(String value) {
        String normalized = normalizeOptional(value);
        if (normalized == null) throw new IllegalArgumentException("프랜차이즈 이름은 비워둘 수 없습니다");
        return normalized;
    }

    private String normalizeOptional(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String identityKey(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private Franchise findFranchise(Long id) {
        return franchiseRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("프랜차이즈를 찾을 수 없습니다: " + id));
    }

    private Game findGame(Long id) {
        return gameRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("게임을 찾을 수 없습니다: " + id));
    }
}
