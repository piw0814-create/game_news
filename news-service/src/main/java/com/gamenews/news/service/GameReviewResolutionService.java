package com.gamenews.news.service;

import com.gamenews.news.dto.GameReviewResolutionDto;
import com.gamenews.news.entity.ArticleFranchise;
import com.gamenews.news.entity.ArticleGame;
import com.gamenews.news.entity.Franchise;
import com.gamenews.news.entity.Game;
import com.gamenews.news.entity.GameReviewStatus;
import com.gamenews.news.entity.GameRegistrationSource;
import com.gamenews.news.entity.NewsArticle;
import com.gamenews.news.entity.TopicFranchise;
import com.gamenews.news.entity.TopicGame;
import com.gamenews.news.repository.ArticleFranchiseRepository;
import com.gamenews.news.repository.ArticleGameRepository;
import com.gamenews.news.repository.FranchiseRepository;
import com.gamenews.news.repository.GameFranchiseRepository;
import com.gamenews.news.repository.GameRepository;
import com.gamenews.news.repository.NewsArticleRepository;
import com.gamenews.news.repository.TopicGameRepository;
import com.gamenews.news.repository.TopicFranchiseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GameReviewResolutionService {

    private final GameRepository gameRepository;
    private final FranchiseRepository franchiseRepository;
    private final NewsArticleRepository newsArticleRepository;
    private final ArticleGameRepository articleGameRepository;
    private final ArticleFranchiseRepository articleFranchiseRepository;
    private final TopicGameRepository topicGameRepository;
    private final TopicFranchiseRepository topicFranchiseRepository;
    private final GameFranchiseRepository gameFranchiseRepository;
    private final InterestServiceClient interestServiceClient;

    @Transactional
    public GameReviewResolutionDto.ResolveAsFranchiseResponse resolveAsFranchise(
            Long gameId,
            Long franchiseId) {
        Game game = findGame(gameId);
        validateReclassifiable(game);
        Franchise franchise = franchiseRepository.findById(franchiseId)
                .orElseThrow(() -> new IllegalArgumentException("프랜차이즈를 찾을 수 없습니다: " + franchiseId));

        List<ArticleGame> articleLinks = articleGameRepository.findAllByGame_IdOrderByIdAsc(gameId);
        int convertedCount = 0;
        for (ArticleGame articleLink : articleLinks) {
            ArticleFranchise franchiseLink = articleFranchiseRepository
                    .findByArticle_IdAndFranchise_Id(articleLink.getArticle().getId(), franchiseId)
                    .orElseGet(() -> ArticleFranchise.builder()
                            .article(articleLink.getArticle())
                            .franchise(franchise)
                            .primary(articleLink.isPrimary())
                            .confidenceScore(articleLink.getConfidenceScore())
                            .relevanceReason(articleLink.getRelevanceReason())
                            .build());

            franchiseLink.absorbMetadata(
                    articleLink.isPrimary(),
                    articleLink.getConfidenceScore(),
                    articleLink.getRelevanceReason());
            articleFranchiseRepository.save(franchiseLink);
            convertedCount++;
        }

        if (articleLinks.isEmpty() && game.getSourceArticleId() != null) {
            NewsArticle sourceArticle = newsArticleRepository.findById(game.getSourceArticleId()).orElse(null);
            if (sourceArticle != null && !articleFranchiseRepository.existsByArticle_IdAndFranchise_Id(
                    sourceArticle.getId(), franchiseId)) {
                articleFranchiseRepository.save(ArticleFranchise.builder()
                        .article(sourceArticle)
                        .franchise(franchise)
                        .primary(true)
                        .confidenceScore(game.getRegistrationConfidence())
                        .relevanceReason("관리자 검토에서 특정 게임이 아닌 프랜차이즈 언급으로 전환")
                        .build());
                convertedCount++;
            }
        }

        List<TopicGame> topicGameLinks = topicGameRepository.findAllByGame_IdOrderByIdAsc(gameId);
        for (TopicGame topicGameLink : topicGameLinks) {
            TopicFranchise topicFranchise = topicFranchiseRepository
                    .findByTopic_IdAndFranchise_Id(topicGameLink.getTopic().getId(), franchiseId)
                    .orElseGet(() -> TopicFranchise.builder()
                            .topic(topicGameLink.getTopic())
                            .franchise(franchise)
                            .primary(topicGameLink.isPrimary())
                            .relevanceScore(topicGameLink.getRelevanceScore())
                            .build());
            topicFranchise.absorbMetadata(topicGameLink.isPrimary(), topicGameLink.getRelevanceScore());
            topicFranchiseRepository.save(topicFranchise);
        }

        interestServiceClient.deleteGameReferences(gameId);
        articleGameRepository.deleteAll(articleLinks);
        topicGameRepository.deleteAll(topicGameLinks);
        gameFranchiseRepository.deleteAll(gameFranchiseRepository.findAllByGame_IdOrderByPrimaryDescCreatedAtAsc(gameId));
        articleGameRepository.flush();
        topicGameRepository.flush();
        topicFranchiseRepository.flush();
        gameFranchiseRepository.flush();
        gameRepository.delete(game);
        gameRepository.flush();

        return GameReviewResolutionDto.ResolveAsFranchiseResponse.builder()
                .removedGameId(gameId)
                .franchiseId(franchise.getId())
                .franchiseName(franchise.getName())
                .convertedArticleCount(convertedCount)
                .build();
    }

    private void validateReclassifiable(Game game) {
        boolean reviewRequired = game.getReviewStatus() == GameReviewStatus.REVIEW_REQUIRED;
        boolean confirmedAi = game.getReviewStatus() == GameReviewStatus.CONFIRMED
                && game.getRegistrationSource() == GameRegistrationSource.AI;

        if (!reviewRequired && !confirmedAi) {
            throw new IllegalArgumentException(
                    "검토 필요 게임 또는 AI 자동확정 게임만 프랜차이즈로 재분류할 수 있습니다");
        }
    }

    private Game findGame(Long id) {
        return gameRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("게임을 찾을 수 없습니다: " + id));
    }
}
