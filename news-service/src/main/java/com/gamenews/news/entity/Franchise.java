package com.gamenews.news.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "franchises")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class Franchise {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String name;

    @Column(name = "display_name", length = 255)
    private String displayName;

    @Builder.Default
    @OneToMany(mappedBy = "franchise", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<FranchiseAlias> aliases = new ArrayList<>();

    @Column(name = "igdb_id", unique = true)
    private Long igdbId;

    @Column(name = "igdb_collection_id", unique = true)
    private Long igdbCollectionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "metadata_source", length = 30)
    private FranchiseMetadataSource metadataSource;

    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;


    public void updateIdentity(String name, String displayName) {
        if (name != null) {
            String normalizedName = name.trim();
            if (normalizedName.isEmpty()) {
                throw new IllegalArgumentException("프랜차이즈 이름은 비워둘 수 없습니다");
            }
            this.name = normalizedName;
        }
        if (displayName != null) {
            this.displayName = trimToNull(displayName);
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

    public void applyIgdbIdentity(Long igdbId, String name) {
        if (igdbId != null) {
            this.igdbId = igdbId;
            this.metadataSource = FranchiseMetadataSource.IGDB;
        }
        applyExternalName(name);
    }

    public void applyIgdbCollectionIdentity(Long igdbCollectionId, String name) {
        if (igdbCollectionId != null) {
            this.igdbCollectionId = igdbCollectionId;
            this.metadataSource = FranchiseMetadataSource.IGDB;
        }
        applyExternalName(name);
    }

    public boolean hasIgdbIdentity() {
        return igdbId != null || igdbCollectionId != null;
    }

    private void applyExternalName(String name) {
        if (name != null && !name.isBlank()) {
            String nextName = name.trim();
            String previousName = this.name;
            this.name = nextName;
            if (previousName != null && !previousName.equalsIgnoreCase(nextName)) {
                addAlias(previousName);
            }
        }
    }

    public void clearAliases() {
        this.aliases.clear();
    }

    public void markCatalogSynced() {
        this.lastSyncedAt = LocalDateTime.now();
    }

    public void addAlias(String alias) {
        if (alias == null || alias.isBlank()) {
            return;
        }
        String normalized = alias.trim();
        if (normalized.equalsIgnoreCase(name)
                || (displayName != null && normalized.equalsIgnoreCase(displayName))) {
            return;
        }
        boolean exists = aliases.stream()
                .anyMatch(existing -> existing.getAlias().equalsIgnoreCase(normalized));
        if (!exists) {
            aliases.add(FranchiseAlias.of(this, normalized));
        }
    }
    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

}
