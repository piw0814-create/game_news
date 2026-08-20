package com.gamenews.news.repository;

import com.gamenews.news.entity.GameFranchise;
import com.gamenews.news.entity.GameFranchiseSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface GameFranchiseRepository extends JpaRepository<GameFranchise, Long> {

    Optional<GameFranchise> findByGame_IdAndFranchise_Id(Long gameId, Long franchiseId);

    List<GameFranchise> findAllByGame_IdOrderByPrimaryDescCreatedAtAsc(Long gameId);

    List<GameFranchise> findAllByFranchise_IdOrderByPrimaryDescCreatedAtAsc(Long franchiseId);

    List<GameFranchise> findAllByFranchise_IdAndSource(Long franchiseId, GameFranchiseSource source);

    @Query("select distinct gf.franchise.id from GameFranchise gf where gf.game.id in :gameIds")
    List<Long> findDistinctFranchiseIdsByGameIds(@Param("gameIds") Collection<Long> gameIds);

    long countByFranchise_Id(Long franchiseId);
}
