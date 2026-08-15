package com.gamenews.news.repository;

import com.gamenews.news.entity.TopicArticle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TopicArticleRepository extends JpaRepository<TopicArticle, Long> {

    boolean existsByTopic_IdAndArticle_Id(Long topicId, Long articleId);

    List<TopicArticle> findAllByTopic_IdOrderByCreatedAtAsc(Long topicId);
}
