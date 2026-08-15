package com.gamenews.news.repository;

import com.gamenews.news.entity.ArticleGame;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ArticleGameRepository extends JpaRepository<ArticleGame, Long> {

    boolean existsByArticle_IdAndGame_Id(Long articleId, Long gameId);

    List<ArticleGame> findAllByArticle_IdOrderByPrimaryDescCreatedAtAsc(Long articleId);
}
