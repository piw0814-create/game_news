package com.gamenews.news.service;

import com.gamenews.news.dto.CanonicalUrlMaintenanceDto;
import com.gamenews.news.entity.NewsArticle;
import com.gamenews.news.repository.NewsArticleRepository;
import com.gamenews.news.util.UrlCanonicalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CanonicalUrlMaintenanceService {

    private final NewsArticleRepository newsArticleRepository;
    private final UrlCanonicalizer urlCanonicalizer;

    @Transactional
    public CanonicalUrlMaintenanceDto.BackfillResponse backfill(boolean dryRun) {
        List<NewsArticle> candidates = newsArticleRepository.findAllByCanonicalUrlIsNullOrderByIdAsc();
        List<NewsArticle> existing = newsArticleRepository.findAllByCanonicalUrlIsNotNullOrderByIdAsc();

        Map<String, List<NewsArticle>> grouped = new LinkedHashMap<>();
        for (NewsArticle article : existing) {
            grouped.computeIfAbsent(article.getCanonicalUrl(), ignored -> new ArrayList<>()).add(article);
        }
        for (NewsArticle article : candidates) {
            String canonicalUrl = urlCanonicalizer.canonicalize(article.getUrl());
            grouped.computeIfAbsent(canonicalUrl, ignored -> new ArrayList<>()).add(article);
        }

        Set<Long> candidateIds = new LinkedHashSet<>();
        for (NewsArticle article : candidates) {
            candidateIds.add(article.getId());
        }

        List<CanonicalUrlMaintenanceDto.ConflictGroup> conflicts = new ArrayList<>();
        Map<Long, String> assignable = new LinkedHashMap<>();
        int conflictedArticles = 0;

        for (Map.Entry<String, List<NewsArticle>> entry : grouped.entrySet()) {
            List<NewsArticle> articles = entry.getValue();
            long candidateCount = articles.stream()
                    .filter(article -> candidateIds.contains(article.getId()))
                    .count();
            if (candidateCount == 0) {
                continue;
            }

            if (articles.size() > 1) {
                conflictedArticles += (int) candidateCount;
                conflicts.add(CanonicalUrlMaintenanceDto.ConflictGroup.builder()
                        .canonicalUrl(entry.getKey())
                        .articles(articles.stream()
                                .map(article -> CanonicalUrlMaintenanceDto.ArticleRef.builder()
                                        .id(article.getId())
                                        .url(article.getUrl())
                                        .build())
                                .toList())
                        .build());
                continue;
            }

            NewsArticle only = articles.get(0);
            if (candidateIds.contains(only.getId())) {
                assignable.put(only.getId(), entry.getKey());
            }
        }

        int updated = 0;
        if (!dryRun && !assignable.isEmpty()) {
            Map<Long, NewsArticle> candidatesById = new LinkedHashMap<>();
            for (NewsArticle article : candidates) {
                candidatesById.put(article.getId(), article);
            }

            List<NewsArticle> toSave = new ArrayList<>();
            for (Map.Entry<Long, String> entry : assignable.entrySet()) {
                NewsArticle article = candidatesById.get(entry.getKey());
                article.updateCanonicalUrl(entry.getValue());
                toSave.add(article);
            }
            newsArticleRepository.saveAll(toSave);
            newsArticleRepository.flush();
            updated = toSave.size();
        }

        return CanonicalUrlMaintenanceDto.BackfillResponse.builder()
                .dryRun(dryRun)
                .candidates(candidates.size())
                .assignable(assignable.size())
                .updated(updated)
                .conflictGroups(conflicts.size())
                .conflictedArticles(conflictedArticles)
                .conflicts(conflicts)
                .build();
    }
}
