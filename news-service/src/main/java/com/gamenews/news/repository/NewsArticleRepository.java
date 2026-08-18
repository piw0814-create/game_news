package com.gamenews.news.repository;

import com.gamenews.news.entity.NewsArticle;
import com.gamenews.news.enums.AnalysisStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface NewsArticleRepository extends JpaRepository<NewsArticle, Long> {

    boolean existsByUrl(String url);

    List<NewsArticle> findAllByOrderByCreatedAtDesc();

    Optional<NewsArticle> findTopBySourceNameAndPublishedAtIsNotNullOrderByPublishedAtDescIdDesc(
            String sourceName);

    @Query("""
            SELECT article
            FROM NewsArticle article
            WHERE article.analysisStatus IN :retryableStatuses
               OR (
                    article.analysisStatus = :processingStatus
                    AND article.updatedAt < :staleBefore
               )
            ORDER BY article.updatedAt ASC
            """)
    List<NewsArticle> findRecoveryCandidates(
            @Param("retryableStatuses") List<AnalysisStatus> retryableStatuses,
            @Param("processingStatus") AnalysisStatus processingStatus,
            @Param("staleBefore") LocalDateTime staleBefore,
            Pageable pageable);
}
