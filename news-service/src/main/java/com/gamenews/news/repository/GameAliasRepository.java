package com.gamenews.news.repository;

import com.gamenews.news.entity.GameAlias;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GameAliasRepository extends JpaRepository<GameAlias, Long> {

    Optional<GameAlias> findByAliasIgnoreCase(String alias);

    List<GameAlias> findAllByGame_IdOrderByIdAsc(Long gameId);
}
