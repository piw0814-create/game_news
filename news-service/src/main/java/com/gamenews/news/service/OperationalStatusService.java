package com.gamenews.news.service;

import com.gamenews.news.dto.OperationalStatusDto;
import com.gamenews.news.entity.EntityReviewStatus;
import com.gamenews.news.enums.AnalysisStatus;
import com.gamenews.news.repository.EntityReviewRepository;
import com.gamenews.news.repository.NewsArticleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class OperationalStatusService {

    private static final int PROCESSING_STALE_MINUTES = 15;

    private final NewsArticleRepository newsArticleRepository;
    private final EntityReviewRepository entityReviewRepository;

    public OperationalStatusDto.Response getStatus() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime processingStaleBefore = now.minusMinutes(PROCESSING_STALE_MINUTES);

        OperationalStatusDto.ArticleStatus articleStatus = OperationalStatusDto.ArticleStatus.builder()
                .pending(count(AnalysisStatus.PENDING))
                .processing(count(AnalysisStatus.PROCESSING))
                .analyzed(count(AnalysisStatus.ANALYZED))
                .topicPending(count(AnalysisStatus.TOPIC_PENDING))
                .completed(count(AnalysisStatus.COMPLETED))
                .failed(count(AnalysisStatus.FAILED))
                .staleProcessing(newsArticleRepository.countByAnalysisStatusAndUpdatedAtBefore(
                        AnalysisStatus.PROCESSING,
                        processingStaleBefore))
                .processingStaleMinutes(PROCESSING_STALE_MINUTES)
                .oldestPendingAt(newsArticleRepository.findOldestUpdatedAtByAnalysisStatus(
                        AnalysisStatus.PENDING))
                .build();

        OperationalStatusDto.EntityReviewStatus reviewStatus =
                OperationalStatusDto.EntityReviewStatus.builder()
                        .pending(entityReviewRepository.countByStatus(EntityReviewStatus.PENDING))
                        .build();

        return OperationalStatusDto.Response.builder()
                .status("UP")
                .generatedAt(now)
                .articles(articleStatus)
                .entityReviews(reviewStatus)
                .build();
    }

    private long count(AnalysisStatus status) {
        return newsArticleRepository.countByAnalysisStatus(status);
    }
}
