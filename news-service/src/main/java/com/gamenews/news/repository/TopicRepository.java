package com.gamenews.news.repository;

import com.gamenews.news.entity.Topic;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface TopicRepository extends JpaRepository<Topic, Long> {

    List<Topic> findAllByOrderByLastUpdatedAtDesc();

    @Query("""
            select distinct tg.topic
            from TopicGame tg
            where tg.game.id in :gameIds
              and tg.topic.lastUpdatedAt >= :cutoff
            order by tg.topic.lastUpdatedAt desc
            """)
    List<Topic> findCandidatesByGameIdsAndUpdatedAfter(
            @Param("gameIds") List<Long> gameIds,
            @Param("cutoff") LocalDateTime cutoff,
            Pageable pageable);

    @Query("""
            select t
            from Topic t
            where t.lastUpdatedAt >= :cutoff
            order by t.lastUpdatedAt desc
            """)
    List<Topic> findRecentCandidatesUpdatedAfter(
            @Param("cutoff") LocalDateTime cutoff,
            Pageable pageable);
}
