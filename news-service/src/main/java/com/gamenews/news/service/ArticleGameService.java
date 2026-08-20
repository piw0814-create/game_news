package com.gamenews.news.service;

import com.gamenews.news.dto.ArticleGameDto;
import com.gamenews.news.entity.ArticleGame;
import com.gamenews.news.entity.Game;
import com.gamenews.news.entity.NewsArticle;
import com.gamenews.news.repository.ArticleGameRepository;
import com.gamenews.news.repository.GameRepository;
import com.gamenews.news.repository.NewsArticleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ArticleGameService {

    private final ArticleGameRepository articleGameRepository;
    private final NewsArticleRepository newsArticleRepository;
    private final GameRepository gameRepository;

    @Transactional
    public ArticleGameDto.ArticleGameResponse linkGame(Long articleId, ArticleGameDto.CreateRequest request) {
        NewsArticle article = findArticleById(articleId);
        Game game = findGameById(request.getGameId());

        if (articleGameRepository.existsByArticle_IdAndGame_Id(articleId, request.getGameId())) {
            throw new IllegalArgumentException("이미 기사에 연결된 게임입니다: " + request.getGameId());
        }

        ArticleGame articleGame = ArticleGame.builder()
                .article(article)
                .game(game)
                .primary(request.isPrimary())
                .confidenceScore(request.getConfidenceScore())
                .relevanceReason(normalizeReason(request.getRelevanceReason()))
                .build();

        return ArticleGameDto.ArticleGameResponse.from(articleGameRepository.save(articleGame));
    }

    public List<ArticleGameDto.ArticleGameResponse> getGamesByArticle(Long articleId) {
        findArticleById(articleId);

        return articleGameRepository.findAllByArticle_IdOrderByPrimaryDescCreatedAtAsc(articleId).stream()
                .map(ArticleGameDto.ArticleGameResponse::from)
                .toList();
    }

    private NewsArticle findArticleById(Long articleId) {
        return newsArticleRepository.findById(articleId)
                .orElseThrow(() -> new IllegalArgumentException("기사를 찾을 수 없습니다: " + articleId));
    }

    private String normalizeReason(String relevanceReason) {
        if (relevanceReason == null || relevanceReason.isBlank()) {
            return null;
        }
        return relevanceReason.trim();
    }

    private Game findGameById(Long gameId) {
        return gameRepository.findById(gameId)
                .orElseThrow(() -> new IllegalArgumentException("게임을 찾을 수 없습니다: " + gameId));
    }
}
