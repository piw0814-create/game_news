package com.gamenews.news.service;

import com.gamenews.news.dto.CanonicalUrlMaintenanceDto;
import com.gamenews.news.entity.NewsArticle;
import com.gamenews.news.repository.NewsArticleRepository;
import com.gamenews.news.util.UrlCanonicalizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CanonicalUrlMaintenanceServiceTest {

    @Mock
    private NewsArticleRepository newsArticleRepository;

    private CanonicalUrlMaintenanceService service;

    @BeforeEach
    void setUp() {
        service = new CanonicalUrlMaintenanceService(newsArticleRepository, new UrlCanonicalizer());
    }

    @Test
    void dryRunReportsCanonicalCollisionsWithoutChangingRows() {
        NewsArticle first = article(1L, "https://example.com/news/1?utm_source=rss", null);
        NewsArticle duplicate = article(2L, "https://example.com/news/1#comments", null);
        NewsArticle safe = article(3L, "https://example.com/news/2?utm_source=rss", null);

        when(newsArticleRepository.findAllByCanonicalUrlIsNullOrderByIdAsc())
                .thenReturn(List.of(first, duplicate, safe));
        when(newsArticleRepository.findAllByCanonicalUrlIsNotNullOrderByIdAsc())
                .thenReturn(List.of());

        CanonicalUrlMaintenanceDto.BackfillResponse response = service.backfill(true);

        assertThat(response.getCandidates()).isEqualTo(3);
        assertThat(response.getAssignable()).isEqualTo(1);
        assertThat(response.getUpdated()).isZero();
        assertThat(response.getConflictGroups()).isEqualTo(1);
        assertThat(response.getConflictedArticles()).isEqualTo(2);
        assertThat(response.getConflicts().get(0).getCanonicalUrl())
                .isEqualTo("https://example.com/news/1");
        assertThat(first.getCanonicalUrl()).isNull();
        assertThat(duplicate.getCanonicalUrl()).isNull();
        assertThat(safe.getCanonicalUrl()).isNull();
        verify(newsArticleRepository, never()).saveAll(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void applyBackfillsOnlyNonConflictingRows() {
        NewsArticle existing = article(10L, "https://example.com/news/1", "https://example.com/news/1");
        NewsArticle conflict = article(11L, "https://example.com/news/1?utm_source=rss", null);
        NewsArticle safe = article(12L, "https://example.com/news/2?utm_source=rss", null);

        when(newsArticleRepository.findAllByCanonicalUrlIsNullOrderByIdAsc())
                .thenReturn(List.of(conflict, safe));
        when(newsArticleRepository.findAllByCanonicalUrlIsNotNullOrderByIdAsc())
                .thenReturn(List.of(existing));

        CanonicalUrlMaintenanceDto.BackfillResponse response = service.backfill(false);

        assertThat(response.getCandidates()).isEqualTo(2);
        assertThat(response.getAssignable()).isEqualTo(1);
        assertThat(response.getUpdated()).isEqualTo(1);
        assertThat(response.getConflictGroups()).isEqualTo(1);
        assertThat(conflict.getCanonicalUrl()).isNull();
        assertThat(safe.getCanonicalUrl()).isEqualTo("https://example.com/news/2");
        verify(newsArticleRepository).saveAll(List.of(safe));
        verify(newsArticleRepository).flush();
    }

    private NewsArticle article(Long id, String url, String canonicalUrl) {
        return NewsArticle.builder()
                .id(id)
                .title("test")
                .url(url)
                .canonicalUrl(canonicalUrl)
                .sourceName("source")
                .build();
    }
}
