package com.gamenews.news.repository;

import com.gamenews.news.entity.TopicGame;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TopicGameRepository extends JpaRepository<TopicGame, Long> {

    boolean existsByTopic_IdAndGame_Id(Long topicId, Long gameId);

    List<TopicGame> findAllByTopic_IdOrderByPrimaryDescCreatedAtAsc(Long topicId);
}
