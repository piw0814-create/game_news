package com.gamenews.news.service;

import com.gamenews.news.dto.TopicArticleDto;
import com.gamenews.news.entity.Topic;
import com.gamenews.news.entity.TopicArticle;
import com.gamenews.news.repository.TopicArticleRepository;
import com.gamenews.news.repository.TopicRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TopicArticleService {

    private final TopicArticleRepository topicArticleRepository;
    private final TopicRepository topicRepository;

    public List<TopicArticleDto.TopicArticleResponse> getArticlesByTopic(Long topicId) {
        findTopicById(topicId);

        return topicArticleRepository.findAllByTopic_IdOrderByCreatedAtAsc(topicId).stream()
                .map(TopicArticleDto.TopicArticleResponse::from)
                .toList();
    }

    private Topic findTopicById(Long topicId) {
        return topicRepository.findById(topicId)
                .orElseThrow(() -> new IllegalArgumentException("Topic을 찾을 수 없습니다: " + topicId));
    }

}
