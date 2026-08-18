package com.gamenews.news.repository;

import com.gamenews.news.entity.TopicLike;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TopicLikeRepository extends JpaRepository<TopicLike, Long> {

    boolean existsByTopic_IdAndUserId(Long topicId, Long userId);

    long countByTopic_Id(Long topicId);

    long deleteByTopic_IdAndUserId(Long topicId, Long userId);
}
