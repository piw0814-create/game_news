package com.gamenews.news.service;

import com.gamenews.news.client.IgdbClient;
import com.gamenews.news.dto.FranchiseAdminDto;
import com.gamenews.news.entity.Franchise;
import com.gamenews.news.entity.FranchiseAlias;
import com.gamenews.news.entity.Game;
import com.gamenews.news.entity.GameFranchise;
import com.gamenews.news.entity.GameFranchiseSource;
import com.gamenews.news.entity.GameRegistrationSource;
import com.gamenews.news.repository.FranchiseRepository;
import com.gamenews.news.repository.GameFranchiseRepository;
import com.gamenews.news.repository.GameRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class FranchiseCatalogSyncService {

    private final IgdbClient igdbClient;
    private final FranchiseRepository franchiseRepository;
    private final GameRepository gameRepository;
    private final GameFranchiseRepository gameFranchiseRepository;
    private final GameEnrichmentService gameEnrichmentService;

    @Transactional
    public FranchiseAdminDto.SyncResponse sync(Long franchiseId) {
        Franchise franchise = findFranchise(franchiseId);
        if (!franchise.hasIgdbIdentity() && !tryAttachExactIgdbIdentity(franchise)) {
            throw new IllegalArgumentException("IGDB에서 정확히 일치하는 Franchise/Series를 찾지 못했습니다");
        }

        List<Long> igdbGameIds;
        if (franchise.getIgdbId() != null) {
            IgdbClient.IgdbFranchise rawFranchise = igdbClient.getFranchiseById(franchise.getIgdbId());
            franchise.applyIgdbIdentity(rawFranchise.getId(), rawFranchise.getName());
            igdbGameIds = rawFranchise.getGames() == null
                    ? List.of()
                    : rawFranchise.getGames().stream().filter(java.util.Objects::nonNull).distinct().toList();
        } else {
            IgdbClient.IgdbCollection rawCollection = igdbClient.getCollectionById(franchise.getIgdbCollectionId());
            franchise.applyIgdbCollectionIdentity(rawCollection.getId(), rawCollection.getName());
            igdbGameIds = rawCollection.getGames() == null
                    ? List.of()
                    : rawCollection.getGames().stream().filter(java.util.Objects::nonNull).distinct().toList();
        }
        List<IgdbClient.IgdbGame> rawGames = igdbClient.getGamesByIds(igdbGameIds);

        int created = 0;
        int updated = 0;
        int skipped = 0;
        Set<Long> currentIgdbGameIds = new HashSet<>(igdbGameIds);

        for (IgdbClient.IgdbGame rawGame : rawGames) {
            if (rawGame == null || rawGame.getId() == null || rawGame.getName() == null || rawGame.getName().isBlank()) {
                skipped++;
                continue;
            }
            try {
                UpsertResult upsert = findOrCreateGame(rawGame);
                Game game = upsert.game();
                gameEnrichmentService.applyRawSnapshot(game, rawGame);
                ensureFranchiseRelation(game, franchise, rawGame);
                if (upsert.created()) created++; else updated++;
            } catch (IllegalArgumentException ex) {
                skipped++;
                log.warn("[IGDB Catalog] Skip game - franchise={}, igdbGameId={}, reason={}",
                        franchise.getId(), rawGame.getId(), ex.getMessage());
            }
        }

        int removed = removeStaleIgdbRelations(franchise, currentIgdbGameIds);
        franchise.markCatalogSynced();
        franchiseRepository.save(franchise);

        return FranchiseAdminDto.SyncResponse.builder()
                .franchiseId(franchise.getId())
                .igdbId(franchise.getIgdbId())
                .igdbCollectionId(franchise.getIgdbCollectionId())
                .igdbGameCount(igdbGameIds.size())
                .createdGameCount(created)
                .updatedGameCount(updated)
                .skippedGameCount(skipped)
                .removedRelationCount(removed)
                .lastSyncedAt(FranchiseAdminDto.toUtc(franchise.getLastSyncedAt()))
                .build();
    }

    private UpsertResult findOrCreateGame(IgdbClient.IgdbGame rawGame) {
        Game byIgdb = gameRepository.findByIgdbId(rawGame.getId()).orElse(null);
        if (byIgdb != null) {
            return new UpsertResult(byIgdb, false);
        }

        // IGDB 카탈로그에서는 name이 아니라 igdbId가 식별자다.
        // 같은 이름의 에디션/포트/DLC가 여러 IGDB ID로 존재해도 각각 보존한다.
        Game created = Game.builder()
                .name(rawGame.getName().trim())
                .registrationSource(GameRegistrationSource.IGDB)
                .build();
        return new UpsertResult(gameRepository.save(created), true);
    }

    private void ensureFranchiseRelation(Game game, Franchise franchise, IgdbClient.IgdbGame rawGame) {
        boolean hasPrimarySignal = franchise.getIgdbId() != null;
        boolean primary = hasPrimarySignal
                && rawGame.getFranchise() != null
                && franchise.getIgdbId().equals(rawGame.getFranchise().getId());

        GameFranchise relation = gameFranchiseRepository
                .findByGame_IdAndFranchise_Id(game.getId(), franchise.getId())
                .orElseGet(() -> GameFranchise.builder()
                        .game(game)
                        .franchise(franchise)
                        .primary(primary)
                        .source(GameFranchiseSource.IGDB)
                        .build());

        if (hasPrimarySignal) {
            if (primary) {
                gameFranchiseRepository.findAllByGame_IdOrderByPrimaryDescCreatedAtAsc(game.getId())
                        .forEach(existing -> existing.updatePrimary(false));
            }
            relation.updatePrimary(primary);
        }
        relation.markIgdbSource();
        gameFranchiseRepository.save(relation);
    }

    private int removeStaleIgdbRelations(Franchise franchise, Set<Long> currentIgdbGameIds) {
        List<GameFranchise> igdbRelations = gameFranchiseRepository
                .findAllByFranchise_IdAndSource(franchise.getId(), GameFranchiseSource.IGDB);
        List<GameFranchise> stale = igdbRelations.stream()
                .filter(link -> link.getGame().getIgdbId() != null)
                .filter(link -> !currentIgdbGameIds.contains(link.getGame().getIgdbId()))
                .toList();
        gameFranchiseRepository.deleteAll(stale);
        return stale.size();
    }


    private boolean tryAttachExactIgdbIdentity(Franchise franchise) {
        if (franchise.hasIgdbIdentity()) return true;
        if (!igdbClient.isConfigured()) return false;

        List<String> identities = new ArrayList<>();
        identities.add(franchise.getName());
        identities.add(franchise.getDisplayName());
        franchise.getAliases().stream().map(FranchiseAlias::getAlias).forEach(identities::add);

        Map<Long, IgdbClient.IgdbFranchise> exactFranchises = new LinkedHashMap<>();
        Map<Long, IgdbClient.IgdbCollection> exactCollections = new LinkedHashMap<>();
        for (String identity : identities) {
            if (identity == null || identity.isBlank()) continue;
            String expected = identityKey(identity);
            for (IgdbClient.IgdbFranchise candidate : igdbClient.findFranchisesByExactName(identity, 20)) {
                if (candidate == null || candidate.getId() == null || candidate.getName() == null) continue;
                if (identityKey(candidate.getName()).equals(expected)) {
                    exactFranchises.putIfAbsent(candidate.getId(), candidate);
                }
            }
            for (IgdbClient.IgdbCollection candidate : igdbClient.findCollectionsByExactName(identity, 20)) {
                if (candidate == null || candidate.getId() == null || candidate.getName() == null) continue;
                if (identityKey(candidate.getName()).equals(expected)) {
                    exactCollections.putIfAbsent(candidate.getId(), candidate);
                }
            }
            if (exactFranchises.size() + exactCollections.size() == 1) break;
        }

        if (exactFranchises.size() + exactCollections.size() != 1) return false;

        if (exactFranchises.size() == 1) {
            IgdbClient.IgdbFranchise matched = exactFranchises.values().iterator().next();
            Franchise alreadyLinked = franchiseRepository.findByIgdbId(matched.getId()).orElse(null);
            if (alreadyLinked != null && !alreadyLinked.getId().equals(franchise.getId())) {
                log.warn("[IGDB Catalog] Exact Franchise match already belongs to another local Franchise - franchiseId={}, existingFranchiseId={}, igdbId={}",
                        franchise.getId(), alreadyLinked.getId(), matched.getId());
                return false;
            }
            franchise.applyIgdbIdentity(matched.getId(), matched.getName());
            franchiseRepository.save(franchise);
            log.info("[IGDB Catalog] Franchise exact match attached - franchiseId={}, igdbId={}, name={}",
                    franchise.getId(), matched.getId(), matched.getName());
            return true;
        }

        IgdbClient.IgdbCollection matched = exactCollections.values().iterator().next();
        Franchise alreadyLinked = franchiseRepository.findByIgdbCollectionId(matched.getId()).orElse(null);
        if (alreadyLinked != null && !alreadyLinked.getId().equals(franchise.getId())) {
            log.warn("[IGDB Catalog] Exact Collection match already belongs to another local Franchise - franchiseId={}, existingFranchiseId={}, collectionId={}",
                    franchise.getId(), alreadyLinked.getId(), matched.getId());
            return false;
        }
        franchise.applyIgdbCollectionIdentity(matched.getId(), matched.getName());
        franchiseRepository.save(franchise);
        log.info("[IGDB Catalog] Collection exact match attached - franchiseId={}, collectionId={}, name={}",
                franchise.getId(), matched.getId(), matched.getName());
        return true;
    }

    private String identityKey(String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }

    private Franchise findFranchise(Long id) {
        return franchiseRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("프랜차이즈를 찾을 수 없습니다: " + id));
    }

    private record UpsertResult(Game game, boolean created) {}
}
