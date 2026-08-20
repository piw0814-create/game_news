package com.gamenews.news.service;

import com.gamenews.news.dto.GameReviewDto;
import com.gamenews.news.entity.ArticleGame;
import com.gamenews.news.entity.Game;
import com.gamenews.news.entity.GameAlias;
import com.gamenews.news.entity.NewsArticle;
import com.gamenews.news.repository.ArticleGameRepository;
import com.gamenews.news.repository.GameRepository;
import com.gamenews.news.repository.NewsArticleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GameReviewService {

    private static final int SIMILAR_GAME_LIMIT = 5;

    private final GameRepository gameRepository;
    private final NewsArticleRepository newsArticleRepository;
    private final ArticleGameRepository articleGameRepository;
    private final GameSimilarityService similarityService;

    public GameReviewDto.ReviewContextResponse getReviewContext(Long gameId) {
        Game game = findGame(gameId);

        GameReviewDto.ArticleContext sourceArticle = game.getSourceArticleId() == null
                ? null
                : newsArticleRepository.findById(game.getSourceArticleId())
                        .map(this::toArticleContext)
                        .orElse(null);

        List<GameReviewDto.LinkedArticleContext> linkedArticles = articleGameRepository
                .findAllByGame_IdOrderByIdAsc(gameId)
                .stream()
                .map(this::toLinkedArticleContext)
                .toList();

        List<GameReviewDto.SimilarGameContext> similarGames = gameRepository.findAll().stream()
                .filter(candidate -> !candidate.getId().equals(gameId))
                .map(candidate -> toSimilarGameCandidate(game, candidate))
                .filter(SimilarGameCandidate::eligible)
                .map(SimilarGameCandidate::context)
                .sorted(Comparator.comparing(GameReviewDto.SimilarGameContext::getSimilarityScore).reversed())
                .limit(SIMILAR_GAME_LIMIT)
                .toList();

        return GameReviewDto.ReviewContextResponse.builder()
                .sourceArticle(sourceArticle)
                .linkedArticles(linkedArticles)
                .similarGames(similarGames)
                .build();
    }

    private SimilarGameCandidate toSimilarGameCandidate(Game source, Game candidate) {
        GameSimilarityService.SimilarityResult result = similarityService.compare(
                identities(source),
                identities(candidate),
                source.getPublisher(),
                candidate.getPublisher(),
                source.getDeveloper(),
                candidate.getDeveloper());

        GameReviewDto.SimilarGameContext context = GameReviewDto.SimilarGameContext.builder()
                .id(candidate.getId())
                .name(candidate.getName())
                .displayName(candidate.getDisplayName())
                .aliases(candidate.getAliases().stream().map(GameAlias::getAlias).toList())
                .publisher(candidate.getPublisher())
                .developer(candidate.getDeveloper())
                .reviewStatus(candidate.getReviewStatus())
                .similarityScore(BigDecimal.valueOf(result.score()).setScale(4, RoundingMode.HALF_UP))
                .reasons(result.reasons())
                .build();

        boolean eligible = result.duplicateCandidate() && !result.reasons().isEmpty();
        return new SimilarGameCandidate(context, eligible);
    }

    private List<String> identities(Game game) {
        List<String> identities = new ArrayList<>();
        identities.add(game.getName());
        identities.add(game.getDisplayName());
        game.getAliases().forEach(alias -> identities.add(alias.getAlias()));
        return identities;
    }

    private GameReviewDto.ArticleContext toArticleContext(NewsArticle article) {
        return GameReviewDto.ArticleContext.builder()
                .id(article.getId())
                .title(article.getTitle())
                .sourceName(article.getSourceName())
                .url(article.getUrl())
                .publishedAt(toUtc(article.getPublishedAt()))
                .build();
    }

    private GameReviewDto.LinkedArticleContext toLinkedArticleContext(ArticleGame articleGame) {
        NewsArticle article = articleGame.getArticle();
        return GameReviewDto.LinkedArticleContext.builder()
                .id(article.getId())
                .title(article.getTitle())
                .sourceName(article.getSourceName())
                .url(article.getUrl())
                .publishedAt(toUtc(article.getPublishedAt()))
                .confidenceScore(articleGame.getConfidenceScore())
                .relevanceReason(articleGame.getRelevanceReason())
                .primary(articleGame.isPrimary())
                .build();
    }

    private Game findGame(Long gameId) {
        return gameRepository.findById(gameId)
                .orElseThrow(() -> new IllegalArgumentException("게임을 찾을 수 없습니다: " + gameId));
    }

    private record SimilarGameCandidate(
            GameReviewDto.SimilarGameContext context,
            boolean eligible) {
    }

    private OffsetDateTime toUtc(LocalDateTime value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }
}
