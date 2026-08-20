package com.gamenews.news.entity;

import com.gamenews.news.enums.NewsCategory;
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
@Table(name = "topics")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class Topic {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500)
    private String title;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String summary;

    @Lob
    @Column(name = "why_important", columnDefinition = "LONGTEXT")
    private String whyImportant;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private NewsCategory category;

    @Column(name = "importance_score")
    private Integer importanceScore;

    @Column(name = "first_seen_at", nullable = false)
    private LocalDateTime firstSeenAt;

    @Column(name = "last_updated_at", nullable = false)
    private LocalDateTime lastUpdatedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public void touch(LocalDateTime time) {
        this.lastUpdatedAt = time;
    }

    public void absorbArticleTime(LocalDateTime time) {
        if (time == null) return;
        if (this.firstSeenAt == null || time.isBefore(this.firstSeenAt)) {
            this.firstSeenAt = time;
        }
        if (this.lastUpdatedAt == null || time.isAfter(this.lastUpdatedAt)) {
            this.lastUpdatedAt = time;
        }
    }

    public void updateAnalysis(
            String title,
            String summary,
            NewsCategory category,
            Integer importanceScore,
            String whyImportant) {
        this.title = title;
        this.summary = summary;
        this.category = category;
        this.importanceScore = importanceScore;
        this.whyImportant = whyImportant;
    }
}
