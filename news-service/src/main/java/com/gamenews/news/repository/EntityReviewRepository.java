package com.gamenews.news.repository;

import com.gamenews.news.entity.EntityReview;
import com.gamenews.news.entity.EntityReviewKind;
import com.gamenews.news.entity.EntityReviewStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EntityReviewRepository extends JpaRepository<EntityReview, Long> {

    List<EntityReview> findAllByStatusOrderByCreatedAtDesc(EntityReviewStatus status);

    long countByStatus(EntityReviewStatus status);

    List<EntityReview> findAllByOrderByCreatedAtDesc();

    List<EntityReview> findAllByResolvedGameId(Long resolvedGameId);

    List<EntityReview> findAllByResolvedFranchiseId(Long resolvedFranchiseId);

    Optional<EntityReview> findFirstByArticle_IdAndEntityKindAndDetectedNameIgnoreCaseAndStatusOrderByIdDesc(
            Long articleId,
            EntityReviewKind entityKind,
            String detectedName,
            EntityReviewStatus status);
}
