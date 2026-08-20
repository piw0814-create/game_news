package com.gamenews.news.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "franchise_aliases")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FranchiseAlias {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "franchise_id", nullable = false)
    private Franchise franchise;

    @Column(nullable = false, unique = true, length = 255)
    private String alias;

    private FranchiseAlias(Franchise franchise, String alias) {
        this.franchise = franchise;
        this.alias = alias;
    }

    public static FranchiseAlias of(Franchise franchise, String alias) {
        return new FranchiseAlias(franchise, alias);
    }
}
