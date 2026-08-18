package com.gamenews.collector.service;

import com.gamenews.collector.client.NewsServiceClient;
import com.gamenews.collector.dto.CollectorDto;
import com.gamenews.collector.source.DestructoidRssClient;
import com.gamenews.collector.source.PcGamerRssClient;
import com.gamenews.collector.source.RssArticle;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CollectorService {

    private static final int MAX_MANUAL_LIMIT = 20;

    private final PcGamerRssClient pcGamerRssClient;
    private final DestructoidRssClient destructoidRssClient;
    private final NewsServiceClient newsServiceClient;

    public CollectorDto.CollectionResult collectPcGamer(int limit) {
        validateLimit(limit);
        return collect(pcGamerRssClient.getSourceName(), pcGamerRssClient.fetchLatest(limit));
    }

    public CollectorDto.CollectionResult collectDestructoid(int limit) {
        validateLimit(limit);
        return collect(destructoidRssClient.getSourceName(), destructoidRssClient.fetchLatest(limit));
    }

    private CollectorDto.CollectionResult collect(String sourceName, List<RssArticle> articles) {
        int saved = 0;
        int skipped = 0;
        int failed = 0;

        for (RssArticle article : articles) {
            try {
                validateArticle(article);

                NewsServiceClient.SaveStatus status = newsServiceClient.createNews(
                        CollectorDto.NewsCreateRequest.builder()
                                .title(article.getTitle().trim())
                                .url(article.getUrl().trim())
                                .sourceName(sourceName)
                                .sourceType("MEDIA")
                                .publishedAt(article.getPublishedAt())
                                .content(article.getContent())
                                .build()
                );

                if (status == NewsServiceClient.SaveStatus.SAVED) {
                    saved++;
                } else {
                    skipped++;
                }
            } catch (Exception e) {
                failed++;
                log.warn("[Collector] 기사 처리 실패 - source: {}, title: {}, url: {}, error: {}",
                        sourceName, article.getTitle(), article.getUrl(), e.getMessage());
            }
        }

        return CollectorDto.CollectionResult.builder()
                .source(sourceName)
                .fetched(articles.size())
                .saved(saved)
                .skipped(skipped)
                .failed(failed)
                .build();
    }

    private void validateLimit(int limit) {
        if (limit < 1 || limit > MAX_MANUAL_LIMIT) {
            throw new IllegalArgumentException("limit은 1 이상 " + MAX_MANUAL_LIMIT + " 이하여야 합니다");
        }
    }

    private void validateArticle(RssArticle article) {
        if (article.getTitle() == null || article.getTitle().isBlank()) {
            throw new IllegalArgumentException("RSS 기사 제목이 비어 있습니다");
        }

        if (article.getUrl() == null || article.getUrl().isBlank()) {
            throw new IllegalArgumentException("RSS 기사 URL이 비어 있습니다");
        }
    }
}
