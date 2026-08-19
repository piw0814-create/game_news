package com.gamenews.news.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
@Table(name = "games")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class Game {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String name;

    @Column(length = 255)
    private String publisher;

    @Column(length = 100)
    private String genre;

    @Column(length = 255)
    private String platform;

    @Column(name = "image_url", length = 1000)
    private String imageUrl;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "registration_source", nullable = false, length = 20)
    private GameRegistrationSource registrationSource = GameRegistrationSource.MANUAL;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false, length = 30)
    private GameReviewStatus reviewStatus = GameReviewStatus.CONFIRMED;

    @Column(name = "registration_confidence", precision = 5, scale = 4)
    private BigDecimal registrationConfidence;

    @Column(name = "source_article_id")
    private Long sourceArticleId;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public void updateDetails(
            String name,
            String publisher,
            String genre,
            String platform,
            String imageUrl) {
        if (name != null) {
            this.name = name;
        }
        if (publisher != null) {
            this.publisher = trimToNull(publisher);
        }
        if (genre != null) {
            this.genre = trimToNull(genre);
        }
        if (platform != null) {
            this.platform = trimToNull(platform);
        }
        if (imageUrl != null) {
            this.imageUrl = trimToNull(imageUrl);
        }
    }

    public void confirmReview() {
        this.reviewStatus = GameReviewStatus.CONFIRMED;
    }

    private String trimToNull(String value) {
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
