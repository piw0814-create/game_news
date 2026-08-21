package com.gamenews.news.service;

import com.gamenews.news.client.IgdbClient;
import com.gamenews.news.dto.FranchiseDto;
import com.gamenews.news.entity.Franchise;
import com.gamenews.news.entity.FranchiseMetadataSource;
import com.gamenews.news.entity.Game;
import com.gamenews.news.entity.GameFranchise;
import com.gamenews.news.entity.GameFranchiseSource;
import com.gamenews.news.repository.FranchiseRepository;
import com.gamenews.news.repository.GameFranchiseRepository;
import com.gamenews.news.repository.GameRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FranchiseService {

    private final FranchiseRepository franchiseRepository;
    private final GameFranchiseRepository gameFranchiseRepository;
    private final GameRepository gameRepository;

    public List<FranchiseDto.FranchiseResponse> getAllFranchises() {
        return franchiseRepository.findAllByOrderByNameAsc().stream()
                .map(FranchiseDto.FranchiseResponse::from)
                .toList();
    }

    public FranchiseDto.FranchiseResponse getFranchise(Long id) {
        return FranchiseDto.FranchiseResponse.from(findFranchise(id));
    }

    public List<FranchiseDto.GameFranchiseResponse> getFranchisesByGame(Long gameId) {
        findGame(gameId);
        return gameFranchiseRepository.findAllByGame_IdOrderByPrimaryDescCreatedAtAsc(gameId).stream()
                .map(FranchiseDto.GameFranchiseResponse::from)
                .toList();
    }

    public List<Long> getFranchiseIdsByGames(List<Long> gameIds) {
        if (gameIds == null || gameIds.isEmpty()) {
            return List.of();
        }

        List<Long> uniqueGameIds = gameIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (uniqueGameIds.isEmpty()) {
            return List.of();
        }

        return gameFranchiseRepository.findDistinctFranchiseIdsByGameIds(uniqueGameIds).stream()
                .sorted()
                .toList();
    }

    @Transactional
    public void syncIgdbFranchises(
            Game game,
            IgdbClient.IgdbNamedEntity primaryFranchise,
            List<IgdbClient.IgdbNamedEntity> otherFranchises) {
        Map<Long, IgdbFranchiseLink> byIgdbId = new LinkedHashMap<>();

        if (primaryFranchise != null && primaryFranchise.getId() != null) {
            byIgdbId.put(primaryFranchise.getId(), new IgdbFranchiseLink(primaryFranchise, true));
        }
        if (otherFranchises != null) {
            for (IgdbClient.IgdbNamedEntity raw : otherFranchises) {
                if (raw == null || raw.getId() == null) {
                    continue;
                }
                byIgdbId.putIfAbsent(raw.getId(), new IgdbFranchiseLink(raw, false));
            }
        }

        if (byIgdbId.isEmpty()) {
            return;
        }

        if (primaryFranchise != null && primaryFranchise.getId() != null) {
            gameFranchiseRepository.findAllByGame_IdOrderByPrimaryDescCreatedAtAsc(game.getId())
                    .forEach(existing -> existing.updatePrimary(false));
        }

        for (IgdbFranchiseLink link : byIgdbId.values()) {
            Franchise franchise = upsertIgdbFranchise(link.raw());
            GameFranchise relation = gameFranchiseRepository
                    .findByGame_IdAndFranchise_Id(game.getId(), franchise.getId())
                    .orElseGet(() -> GameFranchise.builder()
                            .game(game)
                            .franchise(franchise)
                            .primary(link.primary())
                            .source(GameFranchiseSource.IGDB)
                            .build());
            relation.updatePrimary(link.primary());
            gameFranchiseRepository.save(relation);
        }
    }

    @Transactional
    public Franchise upsertIgdbFranchise(IgdbClient.IgdbNamedEntity raw) {
        if (raw == null) {
            throw new IllegalArgumentException("IGDB Franchise id/name이 필요합니다");
        }
        return upsertIgdbFranchise(raw.getId(), raw.getName());
    }

    @Transactional
    public Franchise upsertIgdbFranchise(Long igdbId, String name) {
        if (igdbId == null || name == null || name.isBlank()) {
            throw new IllegalArgumentException("IGDB Franchise id/name이 필요합니다");
        }

        String normalizedName = name.trim();
        Franchise franchise = franchiseRepository.findByIgdbId(igdbId)
                .orElseGet(() -> franchiseRepository.findByNameIgnoreCase(normalizedName)
                        .orElseGet(() -> Franchise.builder()
                                .name(normalizedName)
                                .igdbId(igdbId)
                                .metadataSource(FranchiseMetadataSource.IGDB)
                                .build()));

        if (franchise.getIgdbId() != null && !franchise.getIgdbId().equals(igdbId)) {
            throw new IllegalArgumentException(
                    "동일한 Franchise 이름이 다른 IGDB ID에 연결되어 있습니다: " + normalizedName);
        }

        franchise.applyIgdbIdentity(igdbId, normalizedName);
        return franchiseRepository.save(franchise);
    }

    @Transactional
    public Franchise upsertIgdbCollection(IgdbClient.IgdbCollection raw) {
        if (raw == null) {
            throw new IllegalArgumentException("IGDB Collection id/name이 필요합니다");
        }
        return upsertIgdbCollection(raw.getId(), raw.getName());
    }

    @Transactional
    public Franchise upsertIgdbCollection(Long igdbCollectionId, String name) {
        if (igdbCollectionId == null || name == null || name.isBlank()) {
            throw new IllegalArgumentException("IGDB Collection id/name이 필요합니다");
        }

        String normalizedName = name.trim();
        Franchise franchise = franchiseRepository.findByIgdbCollectionId(igdbCollectionId)
                .orElseGet(() -> franchiseRepository.findByNameIgnoreCase(normalizedName)
                        .orElseGet(() -> Franchise.builder()
                                .name(normalizedName)
                                .igdbCollectionId(igdbCollectionId)
                                .metadataSource(FranchiseMetadataSource.IGDB)
                                .build()));

        if (franchise.getIgdbCollectionId() != null
                && !franchise.getIgdbCollectionId().equals(igdbCollectionId)) {
            throw new IllegalArgumentException(
                    "동일한 Franchise 이름이 다른 IGDB Collection ID에 연결되어 있습니다: " + normalizedName);
        }

        franchise.applyIgdbCollectionIdentity(igdbCollectionId, normalizedName);
        return franchiseRepository.save(franchise);
    }

    private Franchise findFranchise(Long id) {
        return franchiseRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("프랜차이즈를 찾을 수 없습니다: " + id));
    }

    private Game findGame(Long id) {
        return gameRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("게임을 찾을 수 없습니다: " + id));
    }

    private record IgdbFranchiseLink(IgdbClient.IgdbNamedEntity raw, boolean primary) {
    }
}
