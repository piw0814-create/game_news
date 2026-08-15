package com.gamenews.news.repository;

import com.gamenews.news.entity.NewsArticle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NewsArticleRepository extends JpaRepository<NewsArticle, Long> {

    boolean existsByUrl(String url);

    List<NewsArticle> findAllByOrderByCreatedAtDesc();
}
