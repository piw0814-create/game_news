package com.gamenews.news.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "entity_reviews")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class EntityReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "article_id", nullable = false)
    private NewsArticle article;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_kind", nullable = false, length = 20)
    private EntityReviewKind entityKind;

    @Column(name = "detected_name", nullable = false, length = 255)
    private String detectedName;

    @Column(name = "ai_entity_type", length = 40)
    private String aiEntityType;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    @Column(name = "confidence_score", precision = 5, scale = 4)
    private BigDecimal confidenceScore;

    @Column(name = "reason", length = 1000)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private EntityReviewStatus status = EntityReviewStatus.PENDING;

    @Column(name = "candidate_json", columnDefinition = "LONGTEXT")
    private String candidateJson;

    @Column(name = "resolved_game_id")
    private Long resolvedGameId;

    @Column(name = "resolved_franchise_id")
    private Long resolvedFranchiseId;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public void refresh(
            String aiEntityType,
            boolean primary,
            BigDecimal confidenceScore,
            String reason,
            String candidateJson) {
        this.aiEntityType = trimToNull(aiEntityType);
        this.primary = primary;
        this.confidenceScore = confidenceScore;
        this.reason = trimToNull(reason);
        this.candidateJson = candidateJson;
        if (this.status != EntityReviewStatus.RESOLVED) {
            this.status = EntityReviewStatus.PENDING;
        }
    }

    public void resolveGame(Long gameId) {
        this.status = EntityReviewStatus.RESOLVED;
        this.resolvedGameId = gameId;
        this.resolvedFranchiseId = null;
        this.resolvedAt = LocalDateTime.now();
    }

    public void resolveFranchise(Long franchiseId) {
        this.status = EntityReviewStatus.RESOLVED;
        this.resolvedGameId = null;
        this.resolvedFranchiseId = franchiseId;
        this.resolvedAt = LocalDateTime.now();
    }

    public void reject() {
        this.status = EntityReviewStatus.REJECTED;
        this.resolvedGameId = null;
        this.resolvedFranchiseId = null;
        this.resolvedAt = LocalDateTime.now();
    }

    public void reopen(String candidateJson) {
        this.status = EntityReviewStatus.PENDING;
        this.candidateJson = candidateJson;
        this.resolvedGameId = null;
        this.resolvedFranchiseId = null;
        this.resolvedAt = null;
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
