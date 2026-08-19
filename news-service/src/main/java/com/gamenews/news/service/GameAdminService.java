package com.gamenews.news.service;

import com.gamenews.news.dto.GameDto;
import com.gamenews.news.entity.ArticleGame;
import com.gamenews.news.entity.Game;
import com.gamenews.news.entity.GameReviewStatus;
import com.gamenews.news.entity.TopicGame;
import com.gamenews.news.repository.ArticleGameRepository;
import com.gamenews.news.repository.GameRepository;
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
    private final ArticleGameRepository articleGameRepository;
    private final TopicGameRepository topicGameRepository;
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

        String newName = null;
        if (request.getName() != null) {
            String candidateName = request.getName().trim();
            if (candidateName.isEmpty()) {
                throw new IllegalArgumentException("게임 이름은 비워둘 수 없습니다");
            }

            gameRepository.findByNameIgnoreCase(candidateName)
                    .filter(existing -> !existing.getId().equals(id))
                    .ifPresent(existing -> {
                        throw new IllegalArgumentException("이미 등록된 게임입니다: " + candidateName);
                    });
            newName = candidateName;
        }

        game.updateDetails(
                newName,
                request.getPublisher(),
                request.getGenre(),
                request.getPlatform(),
                request.getImageUrl());

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

        articleGameRepository.flush();
        topicGameRepository.flush();
        gameRepository.delete(sourceGame);
        gameRepository.flush();

        return GameDto.GameResponse.from(targetGame);
    }

    @Transactional
    public void rejectGame(Long gameId) {
        Game game = findGameById(gameId);
        if (game.getReviewStatus() == GameReviewStatus.CONFIRMED) {
            throw new IllegalArgumentException("확정된 게임은 거절할 수 없습니다. 수정 또는 병합을 사용하세요");
        }

        // Interest 관계를 먼저 제거해 삭제된 Game ID를 UserGame이 가리키지 않게 한다.
        interestServiceClient.deleteGameReferences(gameId);

        List<ArticleGame> articleGames = articleGameRepository.findAllByGame_IdOrderByIdAsc(gameId);
        List<TopicGame> topicGames = topicGameRepository.findAllByGame_IdOrderByIdAsc(gameId);

        articleGameRepository.deleteAll(articleGames);
        topicGameRepository.deleteAll(topicGames);
        articleGameRepository.flush();
        topicGameRepository.flush();

        gameRepository.delete(game);
        gameRepository.flush();
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
