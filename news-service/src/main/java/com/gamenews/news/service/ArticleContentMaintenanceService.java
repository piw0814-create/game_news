package com.gamenews.news.service;

import com.gamenews.news.dto.ArticleContentMaintenanceDto;
import com.gamenews.news.entity.NewsArticle;
import com.gamenews.news.repository.NewsArticleRepository;
import com.gamenews.news.util.ArticleContentSanitizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ArticleContentMaintenanceService {

    private final NewsArticleRepository newsArticleRepository;
    private final ArticleContentSanitizer articleContentSanitizer;

    /**
     * 기존 기사 content만 정제한다. Kafka event를 재발행하거나 분석 상태를 변경하지 않는다.
     */
    @Transactional
    public ArticleContentMaintenanceDto.SanitizeResponse sanitize(boolean dryRun) {
        List<NewsArticle> candidates = newsArticleRepository.findAllByContentIsNotNullOrderByIdAsc();
        List<NewsArticle> changedArticles = new ArrayList<>();

        long totalCharsBefore = 0L;
        long totalCharsAfter = 0L;

        for (NewsArticle article : candidates) {
            String before = article.getContent();
            String after = articleContentSanitizer.sanitize(before);

            totalCharsBefore += before == null ? 0 : before.length();
            totalCharsAfter += after == null ? 0 : after.length();

            if (!Objects.equals(before, after)) {
                changedArticles.add(article);
                if (!dryRun) {
                    article.updateContent(after);
                }
            }
        }

        int updated = 0;
        if (!dryRun && !changedArticles.isEmpty()) {
            newsArticleRepository.saveAll(changedArticles);
            newsArticleRepository.flush();
            updated = changedArticles.size();
        }

        int changed = changedArticles.size();
        return ArticleContentMaintenanceDto.SanitizeResponse.builder()
                .dryRun(dryRun)
                .candidates(candidates.size())
                .changed(changed)
                .unchanged(candidates.size() - changed)
                .updated(updated)
                .totalCharsBefore(totalCharsBefore)
                .totalCharsAfter(totalCharsAfter)
                .reductionPercent(calculateReductionPercent(totalCharsBefore, totalCharsAfter))
                .build();
    }

    private double calculateReductionPercent(long before, long after) {
        if (before <= 0L) {
            return 0.0;
        }
        double percentage = ((double) (before - after) / before) * 100.0;
        return Math.round(percentage * 10.0) / 10.0;
    }
}
