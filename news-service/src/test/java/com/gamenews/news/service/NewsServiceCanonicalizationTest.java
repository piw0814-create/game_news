package com.gamenews.news.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gamenews.news.dto.NewsArticleDto;
import com.gamenews.news.entity.NewsArticle;
import com.gamenews.news.enums.SourceType;
import com.gamenews.news.repository.NewsArticleRepository;
import com.gamenews.news.util.ArticleContentSanitizer;
import com.gamenews.news.util.UrlCanonicalizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

@ExtendWith(MockitoExtension.class)
class NewsServiceCanonicalizationTest {

    @Mock
    private NewsArticleRepository newsArticleRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private NewsService newsService;

    @BeforeEach
    void setUp() {
        newsService = new NewsService(
                newsArticleRepository,
                eventPublisher,
                new ObjectMapper(),
                new UrlCanonicalizer(),
                new ArticleContentSanitizer());
    }

    @Test
    void storesOriginalUrlAndCanonicalUrlSeparately() {
        NewsArticleDto.CreateRequest request = NewsArticleDto.CreateRequest.builder()
                .title("Article")
                .url("https://Example.com/news/1/?utm_source=rss#comments")
                .sourceName("Source")
                .sourceType(SourceType.MEDIA)
                .build();

        when(newsArticleRepository.existsByUrl(request.getUrl())).thenReturn(false);
        when(newsArticleRepository.existsByCanonicalUrl("https://example.com/news/1")).thenReturn(false);
        when(newsArticleRepository.findAllByCanonicalUrlIsNullOrderByIdAsc()).thenReturn(List.of());
        when(newsArticleRepository.saveAndFlush(any(NewsArticle.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        newsService.createNews(request);

        ArgumentCaptor<NewsArticle> captor = ArgumentCaptor.forClass(NewsArticle.class);
        verify(newsArticleRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getUrl()).isEqualTo(request.getUrl());
        assertThat(captor.getValue().getCanonicalUrl()).isEqualTo("https://example.com/news/1");
    }

    @Test
    void rejectsDifferentTrackingUrlWhenCanonicalAlreadyExists() {
        NewsArticleDto.CreateRequest request = NewsArticleDto.CreateRequest.builder()
                .title("Article")
                .url("https://example.com/news/1?utm_source=twitter")
                .sourceName("Source")
                .sourceType(SourceType.MEDIA)
                .build();

        when(newsArticleRepository.existsByUrl(request.getUrl())).thenReturn(false);
        when(newsArticleRepository.existsByCanonicalUrl("https://example.com/news/1")).thenReturn(true);

        assertThatThrownBy(() -> newsService.createNews(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("이미 등록된 기사입니다");

        verify(newsArticleRepository, never()).saveAndFlush(any(NewsArticle.class));
    }
    @Test
    void rejectsCanonicalDuplicateFromLegacyRowBeforeBackfill() {
        NewsArticleDto.CreateRequest request = NewsArticleDto.CreateRequest.builder()
                .title("Article")
                .url("https://example.com/news/1?utm_source=twitter")
                .sourceName("Source")
                .sourceType(SourceType.MEDIA)
                .build();

        NewsArticle legacy = NewsArticle.builder()
                .id(99L)
                .title("Legacy")
                .url("https://example.com/news/1?utm_source=rss")
                .sourceName("Source")
                .sourceType(SourceType.MEDIA)
                .build();

        when(newsArticleRepository.existsByUrl(request.getUrl())).thenReturn(false);
        when(newsArticleRepository.existsByCanonicalUrl("https://example.com/news/1")).thenReturn(false);
        when(newsArticleRepository.findAllByCanonicalUrlIsNullOrderByIdAsc()).thenReturn(List.of(legacy));

        assertThatThrownBy(() -> newsService.createNews(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("이미 등록된 기사입니다");

        verify(newsArticleRepository, never()).saveAndFlush(any(NewsArticle.class));
    }

}
