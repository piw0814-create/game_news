package com.gamenews.news.service;

import com.gamenews.news.entity.Game;
import com.gamenews.news.entity.GameRegistrationSource;
import com.gamenews.news.event.GameResolvedEvent;
import com.gamenews.news.repository.GameFranchiseRepository;
import com.gamenews.news.repository.GameRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class GameCatalogRefreshService {

    private static final int GAME_REFRESH_HOURS = 24;

    private final GameRepository gameRepository;
    private final GameFranchiseRepository gameFranchiseRepository;
    private final GameEnrichmentService gameEnrichmentService;
    private final FranchiseCatalogSyncService franchiseCatalogSyncService;

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onGameResolved(GameResolvedEvent event) {
        Long gameId = event.gameId();
        try {
            Game game = gameRepository.findById(gameId).orElse(null);
            if (game == null) return;

            if (game.getIgdbId() == null) {
                boolean matched = gameEnrichmentService.autoApplyBestMatch(gameId);
                if (!matched) {
                    if (game.getRegistrationSource() == GameRegistrationSource.AI) {
                        game.requireReview();
                        gameRepository.save(game);
                    }
                    log.info("[IGDB Auto] No safe automatic match; admin review required - gameId={}, name={}",
                            gameId, game.getName());
                    return;
                }
            } else if (game.getLastEnrichedAt() == null
                    || game.getLastEnrichedAt().isBefore(LocalDateTime.now().minusHours(GAME_REFRESH_HOURS))) {
                gameEnrichmentService.refresh(gameId);
            }

            gameFranchiseRepository.findAllByGame_IdOrderByPrimaryDescCreatedAtAsc(gameId).stream()
                    .map(link -> link.getFranchise())
                    .filter(franchise -> franchise.getIgdbId() != null)
                    .forEach(franchise -> {
                        try {
                            franchiseCatalogSyncService.syncIfStale(franchise.getId());
                        } catch (RuntimeException ex) {
                            log.warn("[IGDB Auto] Franchise catalog sync failed - gameId={}, franchiseId={}, reason={}",
                                    gameId, franchise.getId(), ex.getMessage());
                        }
                    });
        } catch (RuntimeException ex) {
            // 카탈로그 갱신 실패가 기사 AI 처리 자체를 실패시키지 않도록 분리한다.
            log.warn("[IGDB Auto] Catalog refresh failed - gameId={}, reason={}", gameId, ex.getMessage());
        }
    }
}
