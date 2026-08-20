package com.gamenews.news.service;

import com.gamenews.news.dto.TopicGameDto;
import com.gamenews.news.entity.Topic;
import com.gamenews.news.repository.TopicGameRepository;
import com.gamenews.news.repository.TopicRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TopicGameService {

    private final TopicGameRepository topicGameRepository;
    private final TopicRepository topicRepository;

    public List<TopicGameDto.TopicGameResponse> getGamesByTopic(Long topicId) {
        findTopicById(topicId);

        return topicGameRepository.findAllByTopic_IdOrderByPrimaryDescCreatedAtAsc(topicId).stream()
                .map(TopicGameDto.TopicGameResponse::from)
                .toList();
    }

    private Topic findTopicById(Long topicId) {
        return topicRepository.findById(topicId)
                .orElseThrow(() -> new IllegalArgumentException("Topic을 찾을 수 없습니다: " + topicId));
    }

}
