package com.gamenews.news.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "article_franchises",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_article_franchise",
                columnNames = {"article_id", "franchise_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class ArticleFranchise {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "article_id", nullable = false)
    private NewsArticle article;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "franchise_id", nullable = false)
    private Franchise franchise;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    @Column(name = "confidence_score", precision = 5, scale = 4)
    private BigDecimal confidenceScore;

    @Column(name = "relevance_reason", columnDefinition = "TEXT")
    private String relevanceReason;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    public void absorbMetadata(boolean primary, BigDecimal confidenceScore, String relevanceReason) {
        this.primary = this.primary || primary;
        if (confidenceScore != null && (this.confidenceScore == null || confidenceScore.compareTo(this.confidenceScore) > 0)) {
            this.confidenceScore = confidenceScore;
        }
        if ((this.relevanceReason == null || this.relevanceReason.isBlank())
                && relevanceReason != null && !relevanceReason.isBlank()) {
            this.relevanceReason = relevanceReason.trim();
        }
    }

}
