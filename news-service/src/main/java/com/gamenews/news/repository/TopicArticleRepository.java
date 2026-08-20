package com.gamenews.news.repository;

import com.gamenews.news.entity.TopicArticle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TopicArticleRepository extends JpaRepository<TopicArticle, Long> {

    List<TopicArticle> findAllByTopic_IdOrderByCreatedAtAsc(Long topicId);

    Optional<TopicArticle> findByArticle_Id(Long articleId);
}
