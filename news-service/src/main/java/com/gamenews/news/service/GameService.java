package com.gamenews.news.service;

import com.gamenews.news.dto.GameDto;
import com.gamenews.news.entity.Game;
import com.gamenews.news.entity.GameRegistrationSource;
import com.gamenews.news.repository.GameRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GameService {

    private final GameRepository gameRepository;
    private final GameIdentityService gameIdentityService;

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
                .build();
        game.replaceAliases(aliases);

        return GameDto.GameResponse.from(gameRepository.save(game));
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
