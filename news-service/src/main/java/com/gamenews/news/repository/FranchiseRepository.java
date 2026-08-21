package com.gamenews.news.repository;

import com.gamenews.news.entity.Franchise;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FranchiseRepository extends JpaRepository<Franchise, Long> {

    Optional<Franchise> findByNameIgnoreCase(String name);

    Optional<Franchise> findByIgdbId(Long igdbId);

    Optional<Franchise> findByIgdbCollectionId(Long igdbCollectionId);

    @Query("""
            select distinct f
            from Franchise f
            left join f.aliases a
            where lower(f.name) = lower(:value)
               or lower(coalesce(f.displayName, '')) = lower(:value)
               or lower(coalesce(a.alias, '')) = lower(:value)
            """)
    List<Franchise> findExactIdentityCandidates(@Param("value") String value);

    List<Franchise> findAllByOrderByNameAsc();
}
