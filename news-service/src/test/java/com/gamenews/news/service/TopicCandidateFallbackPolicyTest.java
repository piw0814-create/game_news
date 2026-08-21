package com.gamenews.news.service;

import com.gamenews.news.dto.TopicIntegrationDto;
import com.gamenews.news.entity.ArticleGame;
import com.gamenews.news.entity.Game;
import com.gamenews.news.entity.NewsArticle;
import com.gamenews.news.entity.Topic;
import com.gamenews.news.repository.ArticleFranchiseRepository;
import com.gamenews.news.repository.ArticleGameRepository;
import com.gamenews.news.repository.NewsArticleRepository;
import com.gamenews.news.repository.TopicArticleRepository;
import com.gamenews.news.repository.TopicFranchiseRepository;
import com.gamenews.news.repository.TopicGameRepository;
import com.gamenews.news.repository.TopicRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TopicCandidateFallbackPolicyTest {

    private TopicRepository topicRepository;
    private NewsArticleRepository newsArticleRepository;
    private ArticleGameRepository articleGameRepository;
    private ArticleFranchiseRepository articleFranchiseRepository;
    private TopicIntegrationService service;

    @BeforeEach
    void setUp() {
        topicRepository = mock(TopicRepository.class);
        newsArticleRepository = mock(NewsArticleRepository.class);
        articleGameRepository = mock(ArticleGameRepository.class);
        articleFranchiseRepository = mock(ArticleFranchiseRepository.class);

        service = new TopicIntegrationService(
                topicRepository,
                mock(TopicArticleRepository.class),
                mock(TopicGameRepository.class),
                mock(TopicFranchiseRepository.class),
                newsArticleRepository,
                articleGameRepository,
                articleFranchiseRepository
        );
    }

    @Test
    void entityArticleDoesNotUseRecentFallbackWhenNoEntityTopicMatches() {
        NewsArticle article = article(1L);
        when(newsArticleRepository.findById(1L)).thenReturn(Optional.of(article));
        when(articleGameRepository.findAllByArticle_IdOrderByPrimaryDescCreatedAtAsc(1L))
                .thenReturn(List.of());
        when(articleFranchiseRepository.findAllByArticle_IdOrderByPrimaryDescCreatedAtAsc(1L))
                .thenReturn(List.of());

        List<TopicIntegrationDto.CandidateResponse> result = service.getCandidates(request(false));

        assertThat(result).isEmpty();
        verify(topicRepository, never()).findRecentCandidatesUpdatedAfter(any(), any(Pageable.class));
    }

    @Test
    void noneEntityArticleCanStillUseRecentFallback() {
        NewsArticle article = article(1L);
        Topic recent = topic(99L, "콘솔 가격 정책 변경");
        when(newsArticleRepository.findById(1L)).thenReturn(Optional.of(article));
        when(articleGameRepository.findAllByArticle_IdOrderByPrimaryDescCreatedAtAsc(1L))
                .thenReturn(List.of());
        when(articleFranchiseRepository.findAllByArticle_IdOrderByPrimaryDescCreatedAtAsc(1L))
                .thenReturn(List.of());
        when(topicRepository.findRecentCandidatesUpdatedAfter(any(), any(Pageable.class)))
                .thenReturn(List.of(recent));

        List<TopicIntegrationDto.CandidateResponse> result = service.getCandidates(request(true));

        assertThat(result).extracting(TopicIntegrationDto.CandidateResponse::getId)
                .containsExactly(99L);
        verify(topicRepository).findRecentCandidatesUpdatedAfter(any(), any(Pageable.class));
    }

    @Test
    void entityArticleStillReturnsSameGameCandidatesForAiMatcher() {
        NewsArticle article = article(1L);
        ArticleGame articleGame = mock(ArticleGame.class);
        Game game = mock(Game.class);
        Topic sameGameTopic = topic(77L, "같은 게임의 기존 사건");

        when(newsArticleRepository.findById(1L)).thenReturn(Optional.of(article));
        when(articleGameRepository.findAllByArticle_IdOrderByPrimaryDescCreatedAtAsc(1L))
                .thenReturn(List.of(articleGame));
        when(articleFranchiseRepository.findAllByArticle_IdOrderByPrimaryDescCreatedAtAsc(1L))
                .thenReturn(List.of());
        when(articleGame.getGame()).thenReturn(game);
        when(game.getId()).thenReturn(10L);
        when(topicRepository.findCandidatesByGameIdsAndUpdatedAfter(anyList(), any(), any(Pageable.class)))
                .thenReturn(List.of(sameGameTopic));

        List<TopicIntegrationDto.CandidateResponse> result = service.getCandidates(request(false));

        assertThat(result).extracting(TopicIntegrationDto.CandidateResponse::getId)
                .containsExactly(77L);
        verify(topicRepository, never()).findRecentCandidatesUpdatedAfter(any(), any(Pageable.class));
    }

    private TopicIntegrationDto.CandidateRequest request(boolean allowRecentFallback) {
        return TopicIntegrationDto.CandidateRequest.builder()
                .articleId(1L)
                .windowHours(48)
                .limit(10)
                .allowRecentFallback(allowRecentFallback)
                .build();
    }

    private NewsArticle article(Long id) {
        NewsArticle article = mock(NewsArticle.class);
        when(article.getId()).thenReturn(id);
        when(article.getPublishedAt()).thenReturn(LocalDateTime.of(2026, 8, 21, 8, 0));
        return article;
    }

    private Topic topic(Long id, String title) {
        Topic topic = mock(Topic.class);
        when(topic.getId()).thenReturn(id);
        when(topic.getTitle()).thenReturn(title);
        when(topic.getFirstSeenAt()).thenReturn(LocalDateTime.of(2026, 8, 21, 7, 0));
        when(topic.getLastUpdatedAt()).thenReturn(LocalDateTime.of(2026, 8, 21, 7, 30));
        return topic;
    }
}
