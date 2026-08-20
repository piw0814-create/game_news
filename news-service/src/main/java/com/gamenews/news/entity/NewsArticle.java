package com.gamenews.news.entity;

import com.gamenews.news.enums.AnalysisStatus;
import com.gamenews.news.enums.ArticleEntityType;
import com.gamenews.news.enums.NewsCategory;
import com.gamenews.news.enums.SourceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "news_articles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class NewsArticle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(nullable = false, unique = true, length = 768)
    private String url;

    @Column(name = "source_name", nullable = false, length = 255)
    private String sourceName;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20)
    private SourceType sourceType;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "collected_at", nullable = false)
    private LocalDateTime collectedAt;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String content;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String summary;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String keywords;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private NewsCategory category;

    @Column(name = "game_news_relevant")
    private Boolean gameNewsRelevant;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", length = 30)
    private ArticleEntityType entityType;

    @Enumerated(EnumType.STRING)
    @Column(name = "analysis_status", nullable = false, length = 20)
    private AnalysisStatus analysisStatus;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public void updateAnalysisStatus(AnalysisStatus analysisStatus) {
        this.analysisStatus = analysisStatus;
    }

    public void completeAnalysis(
            String summary,
            NewsCategory category,
            String keywords,
            Boolean gameNewsRelevant,
            ArticleEntityType entityType) {
        this.summary = summary;
        this.category = category;
        this.keywords = keywords;
        this.gameNewsRelevant = gameNewsRelevant;
        this.entityType = entityType;
        this.analysisStatus = AnalysisStatus.COMPLETED;
    }
}
