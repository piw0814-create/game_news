package com.gamenews.interest.repository;

import com.gamenews.interest.entity.UserGame;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserGameRepository extends JpaRepository<UserGame, Long> {

    List<UserGame> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<UserGame> findByUserIdAndGameId(Long userId, Long gameId);

    boolean existsByUserIdAndGameId(Long userId, Long gameId);
}
