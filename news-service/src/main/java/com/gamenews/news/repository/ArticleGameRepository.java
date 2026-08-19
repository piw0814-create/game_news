package com.gamenews.news.repository;

import com.gamenews.news.entity.ArticleGame;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ArticleGameRepository extends JpaRepository<ArticleGame, Long> {

    boolean existsByArticle_IdAndGame_Id(Long articleId, Long gameId);

    Optional<ArticleGame> findByArticle_IdAndGame_Id(Long articleId, Long gameId);

    List<ArticleGame> findAllByArticle_IdOrderByPrimaryDescCreatedAtAsc(Long articleId);

    List<ArticleGame> findAllByGame_IdOrderByIdAsc(Long gameId);
}
