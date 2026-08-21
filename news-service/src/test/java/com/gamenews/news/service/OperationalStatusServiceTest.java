package com.gamenews.news.service;

import com.gamenews.news.dto.OperationalStatusDto;
import com.gamenews.news.entity.EntityReviewStatus;
import com.gamenews.news.enums.AnalysisStatus;
import com.gamenews.news.repository.EntityReviewRepository;
import com.gamenews.news.repository.NewsArticleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OperationalStatusServiceTest {

    @Mock
    private NewsArticleRepository newsArticleRepository;

    @Mock
    private EntityReviewRepository entityReviewRepository;

    private OperationalStatusService service;

    @BeforeEach
    void setUp() {
        service = new OperationalStatusService(newsArticleRepository, entityReviewRepository);
    }

    @Test
    void returnsPipelineBacklogAndPendingReviewCounts() {
        LocalDateTime oldestPending = LocalDateTime.of(2026, 8, 21, 14, 30);

        when(newsArticleRepository.countByAnalysisStatus(AnalysisStatus.PENDING)).thenReturn(2L);
        when(newsArticleRepository.countByAnalysisStatus(AnalysisStatus.PROCESSING)).thenReturn(1L);
        when(newsArticleRepository.countByAnalysisStatus(AnalysisStatus.ANALYZED)).thenReturn(3L);
        when(newsArticleRepository.countByAnalysisStatus(AnalysisStatus.TOPIC_PENDING)).thenReturn(4L);
        when(newsArticleRepository.countByAnalysisStatus(AnalysisStatus.COMPLETED)).thenReturn(480L);
        when(newsArticleRepository.countByAnalysisStatus(AnalysisStatus.FAILED)).thenReturn(5L);
        when(newsArticleRepository.countByAnalysisStatusAndUpdatedAtBefore(
                eq(AnalysisStatus.PROCESSING), any(LocalDateTime.class)))
                .thenReturn(1L);
        when(newsArticleRepository.findOldestUpdatedAtByAnalysisStatus(AnalysisStatus.PENDING))
                .thenReturn(oldestPending);
        when(entityReviewRepository.countByStatus(EntityReviewStatus.PENDING)).thenReturn(6L);

        OperationalStatusDto.Response response = service.getStatus();

        assertThat(response.getStatus()).isEqualTo("UP");
        assertThat(response.getGeneratedAt()).isNotNull();
        assertThat(response.getArticles().getPending()).isEqualTo(2L);
        assertThat(response.getArticles().getProcessing()).isEqualTo(1L);
        assertThat(response.getArticles().getAnalyzed()).isEqualTo(3L);
        assertThat(response.getArticles().getTopicPending()).isEqualTo(4L);
        assertThat(response.getArticles().getCompleted()).isEqualTo(480L);
        assertThat(response.getArticles().getFailed()).isEqualTo(5L);
        assertThat(response.getArticles().getStaleProcessing()).isEqualTo(1L);
        assertThat(response.getArticles().getProcessingStaleMinutes()).isEqualTo(15);
        assertThat(response.getArticles().getOldestPendingAt()).isEqualTo(oldestPending);
        assertThat(response.getEntityReviews().getPending()).isEqualTo(6L);
    }
}
