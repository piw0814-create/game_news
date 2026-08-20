package com.gamenews.news.service;

import com.gamenews.news.dto.FranchiseAdminDto;
import com.gamenews.news.entity.Franchise;
import com.gamenews.news.entity.FranchiseAlias;
import com.gamenews.news.entity.FranchiseMetadataSource;
import com.gamenews.news.entity.Game;
import com.gamenews.news.entity.GameFranchise;
import com.gamenews.news.repository.FranchiseRepository;
import com.gamenews.news.repository.GameFranchiseRepository;
import com.gamenews.news.repository.GameRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public List<FranchiseAdminDto.SummaryResponse> getFranchises(String search) {
        String keyword = normalizeSearch(search);
        return franchiseRepository.findAllByOrderByNameAsc().stream()
                .filter(franchise -> keyword == null || searchableText(franchise).contains(keyword))
                .map(franchise -> FranchiseAdminDto.SummaryResponse.from(
                        franchise,
                        Math.toIntExact(gameFranchiseRepository.countByFranchise_Id(franchise.getId()))))
                .toList();
    }

    public FranchiseAdminDto.DetailResponse getFranchise(Long franchiseId) {
        Franchise franchise = findFranchise(franchiseId);
        return toDetail(franchise);
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
        if (displayChanged && displayName == null) {
            franchise.clearDisplayName();
        }
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

    private void applyPrimary(Long gameId, GameFranchise link, boolean primary) {
        if (primary) {
            gameFranchiseRepository.findAllByGame_IdOrderByPrimaryDescCreatedAtAsc(gameId)
                    .forEach(existing -> existing.updatePrimary(false));
        }
        link.updatePrimary(primary);
    }

    private FranchiseAdminDto.DetailResponse toDetail(Franchise franchise) {
        List<GameFranchise> links = gameFranchiseRepository
                .findAllByFranchise_IdOrderByPrimaryDescCreatedAtAsc(franchise.getId());
        return FranchiseAdminDto.DetailResponse.from(franchise, links);
    }

    private void validateIdentityAvailable(
            Long excludeId,
            String name,
            String displayName,
            List<String> aliases) {
        Set<String> desired = new LinkedHashSet<>();
        desired.add(identityKey(name));
        if (displayName != null) desired.add(identityKey(displayName));
        aliases.forEach(alias -> desired.add(identityKey(alias)));

        for (Franchise candidate : franchiseRepository.findAll()) {
            if (excludeId != null && candidate.getId().equals(excludeId)) {
                continue;
            }
            for (String identity : identities(candidate)) {
                if (desired.contains(identityKey(identity))) {
                    throw new IllegalArgumentException(
                            "이미 같은 이름/표시명/별칭을 사용하는 프랜차이즈가 있습니다: " + candidate.getName());
                }
            }
        }
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
        if (normalized == null) {
            throw new IllegalArgumentException("프랜차이즈 이름은 비워둘 수 없습니다");
        }
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
