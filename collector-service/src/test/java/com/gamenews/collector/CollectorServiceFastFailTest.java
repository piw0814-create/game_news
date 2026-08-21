package com.gamenews.collector;

import com.gamenews.collector.client.NewsServiceClient;
import com.gamenews.collector.dto.CollectorDto;
import com.gamenews.collector.exception.NewsServiceUnavailableException;
import com.gamenews.collector.service.CollectorService;
import com.gamenews.collector.source.GenericRssClient;
import com.gamenews.collector.source.RssArticle;
import com.gamenews.collector.source.RssSourceConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CollectorServiceFastFailTest {

    @Mock
    private GenericRssClient genericRssClient;

    @Mock
    private NewsServiceClient newsServiceClient;

    private RssSourceConfig sourceConfig;
    private CollectorService collectorService;

    @BeforeEach
    void setUp() {
        sourceConfig = new RssSourceConfig();
        RssSourceConfig.Source source = new RssSourceConfig.Source();
        source.setName("Test Source");
        source.setSourceType("MEDIA");
        source.setRssUrl("https://example.com/feed");
        source.setEnabled(true);
        source.setLimit(10);

        Map<String, RssSourceConfig.Source> sources = new LinkedHashMap<>();
        sources.put("test", source);
        sourceConfig.setSources(sources);

        collectorService = new CollectorService(genericRssClient, sourceConfig, newsServiceClient);
    }

    @Test
    void stopsCurrentSourceImmediatelyWhenNewsServiceIsUnavailable() {
        List<RssArticle> articles = List.of(
                article("one", "https://example.com/1"),
                article("two", "https://example.com/2"),
                article("three", "https://example.com/3")
        );
        when(genericRssClient.fetchLatest(any(), anyInt())).thenReturn(articles);
        when(newsServiceClient.createNews(any()))
                .thenThrow(new NewsServiceUnavailableException("timeout"));

        assertThatThrownBy(() -> collectorService.collectScheduled("test", 3))
                .isInstanceOf(NewsServiceUnavailableException.class)
                .hasMessageContaining("timeout");

        verify(newsServiceClient, times(1)).createNews(any());
    }

    @Test
    void continuesWithNextArticleWhenFailureIsArticleSpecific() {
        List<RssArticle> articles = List.of(
                article("bad request", "https://example.com/bad"),
                article("valid", "https://example.com/valid")
        );
        when(genericRssClient.fetchLatest(any(), anyInt())).thenReturn(articles);
        when(newsServiceClient.createNews(any()))
                .thenThrow(new IllegalStateException("News Service 저장 실패 (HTTP 400)"))
                .thenReturn(NewsServiceClient.SaveStatus.SAVED);

        CollectorDto.CollectionResult result = collectorService.collectScheduled("test", 2);

        assertThat(result.getFetched()).isEqualTo(2);
        assertThat(result.getSaved()).isEqualTo(1);
        assertThat(result.getSkipped()).isZero();
        assertThat(result.getFailed()).isEqualTo(1);
        verify(newsServiceClient, times(2)).createNews(any());
    }

    private RssArticle article(String title, String url) {
        return RssArticle.builder()
                .title(title)
                .url(url)
                .publishedAt(LocalDateTime.now())
                .content("content")
                .build();
    }
}
