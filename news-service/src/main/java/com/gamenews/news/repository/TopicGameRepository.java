package com.gamenews.news.repository;

import com.gamenews.news.entity.TopicGame;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TopicGameRepository extends JpaRepository<TopicGame, Long> {

    boolean existsByTopic_IdAndGame_Id(Long topicId, Long gameId);

    Optional<TopicGame> findByTopic_IdAndGame_Id(Long topicId, Long gameId);

    List<TopicGame> findAllByTopic_IdOrderByPrimaryDescCreatedAtAsc(Long topicId);

    List<TopicGame> findAllByGame_IdOrderByIdAsc(Long gameId);

    @Query("""
            select tg.topic.id as topicId, tg.game.id as gameId
            from TopicGame tg
            where tg.topic.id in :topicIds
            """)
    List<TopicGameIdView> findGameIdsByTopicIds(@Param("topicIds") List<Long> topicIds);

    @Query("""
            select distinct tg
            from TopicGame tg
            join fetch tg.game g
            left join fetch g.aliases
            where tg.topic.id in :topicIds
            """)
    List<TopicGame> findAllWithGameDetailsByTopicIds(@Param("topicIds") List<Long> topicIds);

    interface TopicGameIdView {
        Long getTopicId();
        Long getGameId();
    }
}
