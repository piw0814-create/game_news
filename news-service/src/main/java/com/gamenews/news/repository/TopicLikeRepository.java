package com.gamenews.news.repository;

import com.gamenews.news.entity.TopicLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TopicLikeRepository extends JpaRepository<TopicLike, Long> {

    boolean existsByTopic_IdAndUserId(Long topicId, Long userId);

    long countByTopic_Id(Long topicId);

    @Query("""
            select tl.topic.id as topicId, count(tl.id) as count
            from TopicLike tl
            where tl.topic.id in :topicIds
            group by tl.topic.id
            """)
    List<TopicInteractionCountView> countByTopicIds(@Param("topicIds") List<Long> topicIds);

    long deleteByTopic_IdAndUserId(Long topicId, Long userId);

    interface TopicInteractionCountView {
        Long getTopicId();
        Long getCount();
    }
}
