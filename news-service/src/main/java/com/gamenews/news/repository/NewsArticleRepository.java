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
            WHERE article.id NOT IN :excludedIds
              AND (
                    article.analysisStatus = :pendingStatus
                    OR article.analysisStatus = :failedStatus
                    OR (
                        article.analysisStatus = :processingStatus
                        AND article.updatedAt < :processingStaleBefore
                    )
              )
            ORDER BY article.updatedAt ASC
            """)
    List<NewsArticle> findStartupRecoveryCandidates(
            @Param("pendingStatus") AnalysisStatus pendingStatus,
            @Param("failedStatus") AnalysisStatus failedStatus,
            @Param("processingStatus") AnalysisStatus processingStatus,
            @Param("processingStaleBefore") LocalDateTime processingStaleBefore,
            @Param("excludedIds") List<Long> excludedIds,
            Pageable pageable);

    @Query("""
            SELECT article
            FROM NewsArticle article
            WHERE article.id NOT IN :excludedIds
              AND (
                    article.analysisStatus = :failedStatus
                    OR (
                        article.analysisStatus = :pendingStatus
                        AND article.updatedAt < :pendingStaleBefore
                    )
                    OR (
                        article.analysisStatus = :processingStatus
                        AND article.updatedAt < :processingStaleBefore
                    )
              )
            ORDER BY article.updatedAt ASC
            """)
    List<NewsArticle> findPeriodicRecoveryCandidates(
            @Param("pendingStatus") AnalysisStatus pendingStatus,
            @Param("failedStatus") AnalysisStatus failedStatus,
            @Param("processingStatus") AnalysisStatus processingStatus,
            @Param("pendingStaleBefore") LocalDateTime pendingStaleBefore,
            @Param("processingStaleBefore") LocalDateTime processingStaleBefore,
            @Param("excludedIds") List<Long> excludedIds,
            Pageable pageable);
}
