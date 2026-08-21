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

    boolean existsByCanonicalUrl(String canonicalUrl);

    List<NewsArticle> findAllByCanonicalUrlIsNullOrderByIdAsc();

    List<NewsArticle> findAllByCanonicalUrlIsNotNullOrderByIdAsc();

    List<NewsArticle> findAllByContentIsNotNullOrderByIdAsc();

    List<NewsArticle> findAllByOrderByCreatedAtDesc();

    Optional<NewsArticle> findTopBySourceNameAndPublishedAtIsNotNullOrderByPublishedAtDescIdDesc(
            String sourceName);

    long countByAnalysisStatus(AnalysisStatus analysisStatus);

    long countByAnalysisStatusAndUpdatedAtBefore(
            AnalysisStatus analysisStatus,
            LocalDateTime updatedAt);

    @Query("""
            SELECT MIN(article.updatedAt)
            FROM NewsArticle article
            WHERE article.analysisStatus = :analysisStatus
            """)
    LocalDateTime findOldestUpdatedAtByAnalysisStatus(
            @Param("analysisStatus") AnalysisStatus analysisStatus);

    @Query("""
            SELECT article
            FROM NewsArticle article
            WHERE article.id NOT IN :excludedIds
              AND (
                    article.analysisStatus = :pendingStatus
                    OR article.analysisStatus = :failedStatus
                    OR article.analysisStatus = :analyzedStatus
                    OR article.analysisStatus = :topicPendingStatus
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
            @Param("analyzedStatus") AnalysisStatus analyzedStatus,
            @Param("topicPendingStatus") AnalysisStatus topicPendingStatus,
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
                        (
                            article.analysisStatus = :processingStatus
                            OR article.analysisStatus = :analyzedStatus
                            OR article.analysisStatus = :topicPendingStatus
                        )
                        AND article.updatedAt < :processingStaleBefore
                    )
              )
            ORDER BY article.updatedAt ASC
            """)
    List<NewsArticle> findPeriodicRecoveryCandidates(
            @Param("pendingStatus") AnalysisStatus pendingStatus,
            @Param("failedStatus") AnalysisStatus failedStatus,
            @Param("processingStatus") AnalysisStatus processingStatus,
            @Param("analyzedStatus") AnalysisStatus analyzedStatus,
            @Param("topicPendingStatus") AnalysisStatus topicPendingStatus,
            @Param("pendingStaleBefore") LocalDateTime pendingStaleBefore,
            @Param("processingStaleBefore") LocalDateTime processingStaleBefore,
            @Param("excludedIds") List<Long> excludedIds,
            Pageable pageable);
}
