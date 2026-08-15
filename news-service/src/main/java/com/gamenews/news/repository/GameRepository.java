package com.gamenews.news.repository;

import com.gamenews.news.entity.Game;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameRepository extends JpaRepository<Game, Long> {

    boolean existsByNameIgnoreCase(String name);
}
