package com.gamenews.news.repository;

import com.gamenews.news.entity.TopicComment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TopicCommentRepository extends JpaRepository<TopicComment, Long> {

    List<TopicComment> findAllByTopic_IdOrderByCreatedAtAsc(Long topicId);

    Optional<TopicComment> findByIdAndTopic_Id(Long id, Long topicId);
}
