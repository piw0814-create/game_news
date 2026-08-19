package com.gamenews.news.repository;

import com.gamenews.news.entity.TopicComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TopicCommentRepository extends JpaRepository<TopicComment, Long> {

    List<TopicComment> findAllByTopic_IdOrderByCreatedAtAsc(Long topicId);

    Optional<TopicComment> findByIdAndTopic_Id(Long id, Long topicId);

    long countByTopic_Id(Long topicId);

    @Query("""
            select tc.topic.id as topicId, count(tc.id) as count
            from TopicComment tc
            where tc.topic.id in :topicIds
            group by tc.topic.id
            """)
    List<TopicInteractionCountView> countByTopicIds(@Param("topicIds") List<Long> topicIds);

    interface TopicInteractionCountView {
        Long getTopicId();
        Long getCount();
    }
}
