package com.gamenews.news.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.CascadeType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
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
import java.util.ArrayList;
import java.util.List;

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

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "display_name", length = 255)
    private String displayName;

    @Builder.Default
    @OneToMany(mappedBy = "game", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<GameAlias> aliases = new ArrayList<>();

    @Column(length = 255)
    private String publisher;

    @Column(length = 255)
    private String developer;

    @Column(length = 100)
    private String genre;

    @Column(length = 255)
    private String platform;

    @Column(name = "image_url", length = 1000)
    private String imageUrl;

    @Column(name = "igdb_id", unique = true)
    private Long igdbId;

    @Column(name = "igdb_game_type", length = 100)
    private String igdbGameType;

    @Column(name = "version_parent_igdb_id")
    private Long versionParentIgdbId;

    @Enumerated(EnumType.STRING)
    @Column(name = "metadata_source", length = 30)
    private GameMetadataSource metadataSource;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "enrichment_status", length = 30)
    private GameEnrichmentStatus enrichmentStatus = GameEnrichmentStatus.NOT_ENRICHED;

    @Column(name = "last_enriched_at")
    private LocalDateTime lastEnrichedAt;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "registration_source", nullable = false, length = 20)
    private GameRegistrationSource registrationSource = GameRegistrationSource.MANUAL;


    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public void updateDetails(
            String name,
            String displayName,
            String publisher,
            String genre,
            String platform,
            String imageUrl) {
        if (name != null) {
            this.name = name;
        }
        if (displayName != null) {
            this.displayName = trimToNull(displayName);
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


    public void clearDisplayName() {
        this.displayName = null;
    }

    public void replaceAliases(List<String> aliasValues) {
        List<String> desired = aliasValues == null ? List.of() : aliasValues;

        this.aliases.removeIf(existing -> desired.stream()
                .noneMatch(alias -> existing.getAlias().equalsIgnoreCase(alias)));

        desired.forEach(this::addAlias);
    }

    public void clearAliases() {
        this.aliases.clear();
    }

    public void addAlias(String alias) {
        if (alias == null || alias.isBlank()) {
            return;
        }
        boolean exists = this.aliases.stream()
                .anyMatch(existing -> existing.getAlias().equalsIgnoreCase(alias.trim()));
        if (!exists) {
            this.aliases.add(GameAlias.of(this, alias.trim()));
        }
    }

    public String getDisplayNameOrName() {
        return displayName == null || displayName.isBlank() ? name : displayName;
    }

    public void updateDeveloper(String developer) {
        if (developer != null) {
            this.developer = trimToNull(developer);
        }
    }

    public void applyEnrichment(
            Long igdbId,
            String developer,
            String publisher,
            String genre,
            String platform,
            String imageUrl,
            GameEnrichmentStatus status) {
        this.igdbId = igdbId;
        this.metadataSource = GameMetadataSource.IGDB;
        this.enrichmentStatus = status == null ? GameEnrichmentStatus.PARTIAL : status;
        this.lastEnrichedAt = LocalDateTime.now();

        if (isBlank(this.developer) && !isBlank(developer)) {
            this.developer = developer.trim();
        }
        if (isBlank(this.publisher) && !isBlank(publisher)) {
            this.publisher = publisher.trim();
        }
        if (isBlank(this.genre) && !isBlank(genre)) {
            this.genre = genre.trim();
        }
        if (isBlank(this.platform) && !isBlank(platform)) {
            this.platform = platform.trim();
        }
        if (isBlank(this.imageUrl) && !isBlank(imageUrl)) {
            this.imageUrl = imageUrl.trim();
        }
    }


    public void applyIgdbSnapshot(
            Long igdbId,
            String canonicalName,
            String developer,
            String publisher,
            String genre,
            String platform,
            String imageUrl,
            String igdbGameType,
            Long versionParentIgdbId,
            GameEnrichmentStatus status) {
        this.igdbId = igdbId;
        this.metadataSource = GameMetadataSource.IGDB;
        this.enrichmentStatus = status == null ? GameEnrichmentStatus.PARTIAL : status;
        this.lastEnrichedAt = LocalDateTime.now();
        if (!isBlank(canonicalName)) this.name = canonicalName.trim();
        if (!isBlank(developer)) this.developer = developer.trim();
        if (!isBlank(publisher)) this.publisher = publisher.trim();
        if (!isBlank(genre)) this.genre = genre.trim();
        if (!isBlank(platform)) this.platform = platform.trim();
        if (!isBlank(imageUrl)) this.imageUrl = imageUrl.trim();
        this.igdbGameType = trimToNullSafe(igdbGameType);
        this.versionParentIgdbId = versionParentIgdbId;
    }

    public void markEnrichmentFailed() {
        this.enrichmentStatus = GameEnrichmentStatus.FAILED;
        this.lastEnrichedAt = LocalDateTime.now();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String trimToNull(String value) {
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String trimToNullSafe(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
