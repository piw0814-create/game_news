package com.gamenews.news.repository;

import com.gamenews.news.entity.Game;
import com.gamenews.news.entity.GameReviewStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GameRepository extends JpaRepository<Game, Long> {

    boolean existsByNameIgnoreCase(String name);

    Optional<Game> findByNameIgnoreCase(String name);

    List<Game> findAllByOrderByCreatedAtDesc();

    List<Game> findAllByReviewStatusOrderByCreatedAtDesc(GameReviewStatus reviewStatus);
}
