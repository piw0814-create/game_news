package com.gamenews.collector.service;

import com.gamenews.collector.client.NewsServiceClient;
import com.gamenews.collector.dto.CollectorDto;
import com.gamenews.collector.source.GenericRssClient;
import com.gamenews.collector.source.RssArticle;
import com.gamenews.collector.source.RssSourceConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CollectorService {

    private static final int MAX_MANUAL_LIMIT = 20;

    private final GenericRssClient genericRssClient;
    private final RssSourceConfig sourceConfig;
    private final NewsServiceClient newsServiceClient;

    public CollectorDto.CollectionResult collect(String sourceKey, int limit) {
        validateLimit(limit);

        String normalizedKey = normalizeSourceKey(sourceKey);
        RssSourceConfig.Source source = sourceConfig.getRequiredSource(normalizedKey);
        List<RssArticle> articles = genericRssClient.fetchLatest(source, limit);

        return collect(source, articles);
    }

    public List<CollectorDto.CollectionResult> collectAll(int limit) {
        validateLimit(limit);

        List<CollectorDto.CollectionResult> results = new ArrayList<>();
        for (Map.Entry<String, RssSourceConfig.Source> entry : sourceConfig.getSources().entrySet()) {
            try {
                results.add(collect(entry.getKey(), limit));
            } catch (Exception e) {
                log.warn("[Collector] 전체 수집 중 출처 실패 - key: {}, source: {}, error: {}",
                        entry.getKey(), entry.getValue().getName(), e.getMessage());
                results.add(CollectorDto.CollectionResult.builder()
                        .source(entry.getValue().getName())
                        .fetched(0)
                        .saved(0)
                        .skipped(0)
                        .failed(1)
                        .build());
            }
        }
        return results;
    }

    public CollectorDto.CollectionResult collectScheduled(String sourceKey) {
        String normalizedKey = normalizeSourceKey(sourceKey);
        RssSourceConfig.Source source = sourceConfig.getRequiredSource(normalizedKey);
        return collect(normalizedKey, source.getLimit());
    }

    private CollectorDto.CollectionResult collect(
            RssSourceConfig.Source source,
            List<RssArticle> articles) {

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
                                .sourceName(source.getName())
                                .sourceType(source.getSourceType())
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
                        source.getName(), article.getTitle(), article.getUrl(), e.getMessage());
            }
        }

        return CollectorDto.CollectionResult.builder()
                .source(source.getName())
                .fetched(articles.size())
                .saved(saved)
                .skipped(skipped)
                .failed(failed)
                .build();
    }

    private String normalizeSourceKey(String sourceKey) {
        if (sourceKey == null || sourceKey.isBlank()) {
            throw new IllegalArgumentException("수집 출처 키가 비어 있습니다");
        }
        return sourceKey.trim().toLowerCase(Locale.ROOT);
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
