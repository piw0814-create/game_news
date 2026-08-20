package com.gamenews.news.repository;

import com.gamenews.news.entity.ArticleFranchise;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ArticleFranchiseRepository extends JpaRepository<ArticleFranchise, Long> {

    boolean existsByArticle_IdAndFranchise_Id(Long articleId, Long franchiseId);

    Optional<ArticleFranchise> findByArticle_IdAndFranchise_Id(Long articleId, Long franchiseId);

    List<ArticleFranchise> findAllByArticle_IdOrderByPrimaryDescCreatedAtAsc(Long articleId);
}
