package com.gamenews.news.repository;

import com.gamenews.news.entity.GameFranchise;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GameFranchiseRepository extends JpaRepository<GameFranchise, Long> {

    Optional<GameFranchise> findByGame_IdAndFranchise_Id(Long gameId, Long franchiseId);

    List<GameFranchise> findAllByGame_IdOrderByPrimaryDescCreatedAtAsc(Long gameId);

    List<GameFranchise> findAllByFranchise_IdOrderByPrimaryDescCreatedAtAsc(Long franchiseId);

    long countByFranchise_Id(Long franchiseId);
}
