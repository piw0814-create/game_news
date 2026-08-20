package com.gamenews.news.service;

import com.gamenews.news.dto.GameDto;
import com.gamenews.news.entity.Game;
import com.gamenews.news.entity.GameRegistrationSource;
import com.gamenews.news.entity.GameReviewStatus;
import com.gamenews.news.event.GameResolvedEvent;
import com.gamenews.news.exception.AmbiguousGameIdentityException;
import com.gamenews.news.repository.GameRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GameService {

    private final GameRepository gameRepository;
    private final GameIdentityService gameIdentityService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public synchronized GameDto.GameResponse createGame(GameDto.CreateRequest request) {
        String normalizedName = request.getName().trim();

        String displayName = gameIdentityService.normalizeDisplayName(
                normalizedName, request.getDisplayName());
        List<String> aliases = gameIdentityService.normalizeAliases(
                normalizedName, displayName, request.getAliases());

        gameIdentityService.validateAvailable(null, normalizedName, displayName, aliases);

        Game game = Game.builder()
                .name(normalizedName)
                .displayName(displayName)
                .publisher(trimToNull(request.getPublisher()))
                .developer(trimToNull(request.getDeveloper()))
                .genre(trimToNull(request.getGenre()))
                .platform(trimToNull(request.getPlatform()))
                .imageUrl(trimToNull(request.getImageUrl()))
                .registrationSource(GameRegistrationSource.MANUAL)
                .reviewStatus(GameReviewStatus.CONFIRMED)
                .build();
        game.replaceAliases(aliases);

        return GameDto.GameResponse.from(gameRepository.save(game));
    }

    @Transactional
    public synchronized GameDto.ResolveOrCreateResponse resolveOrCreateAiGame(
            GameDto.ResolveOrCreateAiRequest request) {
        String normalizedName = request.getName().trim();

        List<Game> exactCandidates = gameIdentityService.findExactCandidates(normalizedName);
        if (exactCandidates.size() > 1) {
            throw new AmbiguousGameIdentityException(
                    "동일한 게임 이름의 후보가 여러 개라 자동 연결할 수 없습니다: " + normalizedName);
        }
        Game existing = exactCandidates.isEmpty() ? null : exactCandidates.get(0);
        if (existing != null) {
            eventPublisher.publishEvent(new GameResolvedEvent(existing.getId()));
            return GameDto.ResolveOrCreateResponse.builder()
                    .created(false)
                    .game(GameDto.GameResponse.from(existing))
                    .build();
        }

        if (request.getReviewStatus() != GameReviewStatus.CONFIRMED
                && request.getReviewStatus() != GameReviewStatus.REVIEW_REQUIRED) {
            throw new IllegalArgumentException(
                    "AI 자동 등록은 CONFIRMED 또는 REVIEW_REQUIRED 상태만 사용할 수 있습니다");
        }

        Game game = Game.builder()
                .name(normalizedName)
                .registrationSource(GameRegistrationSource.AI)
                .reviewStatus(request.getReviewStatus())
                .registrationConfidence(request.getRegistrationConfidence())
                .sourceArticleId(request.getSourceArticleId())
                .build();

        Game saved = gameRepository.save(game);
        eventPublisher.publishEvent(new GameResolvedEvent(saved.getId()));
        return GameDto.ResolveOrCreateResponse.builder()
                .created(true)
                .game(GameDto.GameResponse.from(saved))
                .build();
    }

    public List<GameDto.GameResponse> getAllGames() {
        return gameRepository.findAll().stream()
                .map(GameDto.GameResponse::from)
                .toList();
    }

    public GameDto.GameResponse getGame(Long id) {
        return GameDto.GameResponse.from(findGameById(id));
    }

    private Game findGameById(Long id) {
        return gameRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("게임을 찾을 수 없습니다: " + id));
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
