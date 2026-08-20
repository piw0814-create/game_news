package com.gamenews.news.service;

import com.gamenews.news.dto.GameDto;
import com.gamenews.news.entity.ArticleGame;
import com.gamenews.news.entity.Game;
import com.gamenews.news.entity.GameAlias;
import com.gamenews.news.entity.GameFranchise;
import com.gamenews.news.entity.GameReviewStatus;
import com.gamenews.news.entity.GameRegistrationSource;
import com.gamenews.news.entity.TopicGame;
import com.gamenews.news.repository.ArticleGameRepository;
import com.gamenews.news.repository.GameRepository;
import com.gamenews.news.repository.GameFranchiseRepository;
import com.gamenews.news.repository.TopicGameRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GameAdminService {

    private final GameRepository gameRepository;
    private final GameIdentityService gameIdentityService;
    private final ArticleGameRepository articleGameRepository;
    private final TopicGameRepository topicGameRepository;
    private final GameFranchiseRepository gameFranchiseRepository;
    private final InterestServiceClient interestServiceClient;

    public List<GameDto.GameResponse> getGames(GameReviewStatus reviewStatus) {
        List<Game> games = reviewStatus == null
                ? gameRepository.findAllByOrderByCreatedAtDesc()
                : gameRepository.findAllByReviewStatusOrderByCreatedAtDesc(reviewStatus);

        return games.stream()
                .map(GameDto.GameResponse::from)
                .toList();
    }

    public GameDto.GameResponse getGame(Long id) {
        return GameDto.GameResponse.from(findGameById(id));
    }

    @Transactional
    public GameDto.GameResponse updateGame(Long id, GameDto.AdminUpdateRequest request) {
        Game game = findGameById(id);

        String finalName = game.getName();
        String newName = null;
        if (request.getName() != null) {
            String candidateName = request.getName().trim();
            if (candidateName.isEmpty()) {
                throw new IllegalArgumentException("게임 이름은 비워둘 수 없습니다");
            }
            finalName = candidateName;
            newName = candidateName;
        }

        boolean displayNameChanged = request.getDisplayName() != null;
        boolean displayNameNeedsSync = displayNameChanged || newName != null;
        String finalDisplayName = displayNameChanged
                ? gameIdentityService.normalizeDisplayName(finalName, request.getDisplayName())
                : gameIdentityService.normalizeDisplayName(finalName, game.getDisplayName());

        List<String> currentAliases = game.getAliases().stream()
                .map(GameAlias::getAlias)
                .toList();
        boolean aliasesChanged = request.getAliases() != null;
        List<String> finalAliases = gameIdentityService.normalizeAliases(
                finalName,
                finalDisplayName,
                aliasesChanged ? request.getAliases() : currentAliases);

        gameIdentityService.validateAvailable(id, finalName, finalDisplayName, finalAliases);

        game.updateDetails(
                newName,
                displayNameNeedsSync && finalDisplayName != null ? finalDisplayName : null,
                request.getPublisher(),
                request.getGenre(),
                request.getPlatform(),
                request.getImageUrl());
        game.updateDeveloper(request.getDeveloper());

        if (displayNameNeedsSync && finalDisplayName == null) {
            game.clearDisplayName();
        }

        if (aliasesChanged || newName != null || displayNameChanged) {
            game.replaceAliases(finalAliases);
        }

        return GameDto.GameResponse.from(game);
    }

    @Transactional
    public GameDto.GameResponse confirmGame(Long id) {
        Game game = findGameById(id);
        game.confirmReview();
        return GameDto.GameResponse.from(game);
    }

    @Transactional
    public GameDto.GameResponse mergeGame(Long sourceGameId, Long targetGameId) {
        if (sourceGameId.equals(targetGameId)) {
            throw new IllegalArgumentException("같은 게임으로 병합할 수 없습니다");
        }

        Game sourceGame = findGameById(sourceGameId);
        Game targetGame = findGameById(targetGameId);

        // 분산 트랜잭션이 없으므로 Interest 쪽을 먼저 처리한다.
        // 이후 News 쪽 실패 시 source Game은 남아 있어 dangling reference가 생기지 않고 재시도도 가능하다.
        interestServiceClient.mergeGameReferences(sourceGameId, targetGameId);

        mergeArticleGames(sourceGameId, targetGame);
        mergeTopicGames(sourceGameId, targetGame);
        mergeGameFranchises(sourceGameId, targetGame);
        mergeGameIdentities(sourceGame, targetGame);

        articleGameRepository.flush();
        topicGameRepository.flush();
        gameFranchiseRepository.flush();
        gameRepository.delete(sourceGame);
        gameRepository.flush();

        return GameDto.GameResponse.from(targetGame);
    }

    @Transactional
    public void rejectGame(Long gameId) {
        Game game = findGameById(gameId);
        if (game.getReviewStatus() == GameReviewStatus.CONFIRMED
                && game.getRegistrationSource() != GameRegistrationSource.AI) {
            throw new IllegalArgumentException(
                    "수동 확정 게임은 관련 없음으로 처리할 수 없습니다. 수정 또는 병합을 사용하세요");
        }

        // Interest 관계를 먼저 제거해 삭제된 Game ID를 UserGame이 가리키지 않게 한다.
        interestServiceClient.deleteGameReferences(gameId);

        List<ArticleGame> articleGames = articleGameRepository.findAllByGame_IdOrderByIdAsc(gameId);
        List<TopicGame> topicGames = topicGameRepository.findAllByGame_IdOrderByIdAsc(gameId);
        List<GameFranchise> gameFranchises = gameFranchiseRepository.findAllByGame_IdOrderByPrimaryDescCreatedAtAsc(gameId);

        articleGameRepository.deleteAll(articleGames);
        topicGameRepository.deleteAll(topicGames);
        gameFranchiseRepository.deleteAll(gameFranchises);
        articleGameRepository.flush();
        topicGameRepository.flush();
        gameFranchiseRepository.flush();

        gameRepository.delete(game);
        gameRepository.flush();
    }


    private void mergeGameIdentities(Game sourceGame, Game targetGame) {
        List<String> sourceAliases = sourceGame.getAliases().stream()
                .map(GameAlias::getAlias)
                .toList();

        if ((targetGame.getDisplayName() == null || targetGame.getDisplayName().isBlank())
                && sourceGame.getDisplayName() != null
                && !sourceGame.getDisplayName().isBlank()) {
            String inheritedDisplayName = gameIdentityService.normalizeDisplayName(
                    targetGame.getName(), sourceGame.getDisplayName());
            if (inheritedDisplayName != null) {
                targetGame.updateDetails(null, inheritedDisplayName, null, null, null, null);
            }
        }

        sourceGame.clearAliases();
        gameRepository.flush();

        List<String> identityCandidates = new java.util.ArrayList<>();
        identityCandidates.add(sourceGame.getName());
        identityCandidates.add(sourceGame.getDisplayName());
        identityCandidates.addAll(sourceAliases);

        List<String> normalizedAliases = gameIdentityService.normalizeAliases(
                targetGame.getName(),
                targetGame.getDisplayName(),
                identityCandidates);
        if (normalizedAliases != null) {
            normalizedAliases.forEach(targetGame::addAlias);
        }
    }

    private void mergeArticleGames(Long sourceGameId, Game targetGame) {
        List<ArticleGame> sourceLinks = articleGameRepository.findAllByGame_IdOrderByIdAsc(sourceGameId);

        for (ArticleGame sourceLink : sourceLinks) {
            articleGameRepository.findByArticle_IdAndGame_Id(sourceLink.getArticle().getId(), targetGame.getId())
                    .ifPresentOrElse(targetLink -> {
                        targetLink.absorbMetadataFrom(sourceLink);
                        articleGameRepository.delete(sourceLink);
                    }, () -> sourceLink.reassignGame(targetGame));
        }
    }

    private void mergeGameFranchises(Long sourceGameId, Game targetGame) {
        List<GameFranchise> sourceLinks = gameFranchiseRepository
                .findAllByGame_IdOrderByPrimaryDescCreatedAtAsc(sourceGameId);
        boolean targetAlreadyHasPrimary = gameFranchiseRepository
                .findAllByGame_IdOrderByPrimaryDescCreatedAtAsc(targetGame.getId()).stream()
                .anyMatch(GameFranchise::isPrimary);

        for (GameFranchise sourceLink : sourceLinks) {
            gameFranchiseRepository.findByGame_IdAndFranchise_Id(
                            targetGame.getId(), sourceLink.getFranchise().getId())
                    .ifPresentOrElse(targetLink -> {
                        if (sourceLink.isPrimary() && !targetAlreadyHasPrimary) {
                            targetLink.updatePrimary(true);
                        }
                        gameFranchiseRepository.delete(sourceLink);
                    }, () -> {
                        if (sourceLink.isPrimary() && targetAlreadyHasPrimary) {
                            sourceLink.updatePrimary(false);
                        }
                        sourceLink.reassignGame(targetGame);
                    });
        }
    }

    private void mergeTopicGames(Long sourceGameId, Game targetGame) {
        List<TopicGame> sourceLinks = topicGameRepository.findAllByGame_IdOrderByIdAsc(sourceGameId);

        for (TopicGame sourceLink : sourceLinks) {
            topicGameRepository.findByTopic_IdAndGame_Id(sourceLink.getTopic().getId(), targetGame.getId())
                    .ifPresentOrElse(targetLink -> {
                        targetLink.absorbMetadataFrom(sourceLink);
                        topicGameRepository.delete(sourceLink);
                    }, () -> sourceLink.reassignGame(targetGame));
        }
    }

    private Game findGameById(Long id) {
        return gameRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("게임을 찾을 수 없습니다: " + id));
    }
}
