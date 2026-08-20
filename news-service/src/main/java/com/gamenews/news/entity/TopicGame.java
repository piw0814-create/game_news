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
        name = "topic_games",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_topic_game",
                columnNames = {"topic_id", "game_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class TopicGame {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "topic_id", nullable = false)
    private Topic topic;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "game_id", nullable = false)
    private Game game;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    @Column(name = "relevance_score", precision = 5, scale = 4)
    private BigDecimal relevanceScore;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public void reassignGame(Game game) {
        this.game = game;
    }

    public void absorbMetadata(boolean primary, BigDecimal relevanceScore) {
        this.primary = this.primary || primary;
        if (relevanceScore != null
                && (this.relevanceScore == null || relevanceScore.compareTo(this.relevanceScore) > 0)) {
            this.relevanceScore = relevanceScore;
        }
    }

    public void absorbMetadataFrom(TopicGame other) {
        this.primary = this.primary || other.primary;
        if (other.relevanceScore != null
                && (this.relevanceScore == null || other.relevanceScore.compareTo(this.relevanceScore) > 0)) {
            this.relevanceScore = other.relevanceScore;
        }
    }
}

