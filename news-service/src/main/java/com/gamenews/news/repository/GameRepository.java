package com.gamenews.news.repository;

import com.gamenews.news.entity.Game;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GameRepository extends JpaRepository<Game, Long> {

    List<Game> findAllByNameIgnoreCase(String name);

    List<Game> findAllByDisplayNameIgnoreCase(String displayName);

    Optional<Game> findByIgdbId(Long igdbId);

    List<Game> findAllByOrderByCreatedAtDesc();

}
