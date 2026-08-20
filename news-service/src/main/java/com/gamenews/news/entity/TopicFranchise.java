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
        name = "topic_franchises",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_topic_franchise",
                columnNames = {"topic_id", "franchise_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class TopicFranchise {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "topic_id", nullable = false)
    private Topic topic;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "franchise_id", nullable = false)
    private Franchise franchise;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    @Column(name = "relevance_score", precision = 5, scale = 4)
    private BigDecimal relevanceScore;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public void reassignFranchise(Franchise franchise) {
        this.franchise = franchise;
    }

    public void absorbMetadata(boolean primary, BigDecimal relevanceScore) {
        this.primary = this.primary || primary;
        if (relevanceScore != null
                && (this.relevanceScore == null || relevanceScore.compareTo(this.relevanceScore) > 0)) {
            this.relevanceScore = relevanceScore;
        }
    }
}
