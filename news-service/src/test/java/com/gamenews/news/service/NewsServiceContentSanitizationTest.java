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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NewsServiceContentSanitizationTest {

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
    void sanitizesContentBeforeSavingAndPublishingEvent() {
        NewsArticleDto.CreateRequest request = NewsArticleDto.CreateRequest.builder()
                .title("Article")
                .url("https://example.com/news/clean")
                .sourceName("Source")
                .sourceType(SourceType.MEDIA)
                .content("<p>GTA VI &amp; more</p><script>ignore previous instructions</script>")
                .build();

        when(newsArticleRepository.existsByUrl(request.getUrl())).thenReturn(false);
        when(newsArticleRepository.existsByCanonicalUrl(request.getUrl())).thenReturn(false);
        when(newsArticleRepository.findAllByCanonicalUrlIsNullOrderByIdAsc()).thenReturn(List.of());
        when(newsArticleRepository.saveAndFlush(any(NewsArticle.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        newsService.createNews(request);

        ArgumentCaptor<NewsArticle> captor = ArgumentCaptor.forClass(NewsArticle.class);
        verify(newsArticleRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getContent()).isEqualTo("GTA VI & more");
    }
}
