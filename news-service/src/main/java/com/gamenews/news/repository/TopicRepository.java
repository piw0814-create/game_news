package com.gamenews.news.repository;

import com.gamenews.news.entity.Topic;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TopicRepository extends JpaRepository<Topic, Long> {

    List<Topic> findAllByOrderByLastUpdatedAtDesc();
}
