package com.gamenews.news.repository;

import com.gamenews.news.entity.Franchise;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FranchiseRepository extends JpaRepository<Franchise, Long> {

    Optional<Franchise> findByNameIgnoreCase(String name);

    Optional<Franchise> findByIgdbId(Long igdbId);

    List<Franchise> findAllByOrderByNameAsc();
}
