package com.gamenews.news.service;

import com.gamenews.news.dto.TopicAnalysisDto;
import com.gamenews.news.dto.TopicDto;
import com.gamenews.news.entity.Topic;
import com.gamenews.news.entity.TopicArticle;
import com.gamenews.news.entity.TopicGame;
import com.gamenews.news.entity.TopicFranchise;
import com.gamenews.news.repository.TopicArticleRepository;
import com.gamenews.news.repository.TopicGameRepository;
import com.gamenews.news.repository.TopicFranchiseRepository;
import com.gamenews.news.repository.TopicRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TopicAnalysisService {

    private final TopicRepository topicRepository;
    private final TopicArticleRepository topicArticleRepository;
    private final TopicGameRepository topicGameRepository;
    private final TopicFranchiseRepository topicFranchiseRepository;

    public TopicAnalysisDto.ContextResponse getAnalysisContext(Long topicId) {
        Topic topic = findTopicById(topicId);
        List<TopicGame> topicGames = topicGameRepository
                .findAllByTopic_IdOrderByPrimaryDescCreatedAtAsc(topicId);
        List<TopicFranchise> topicFranchises = topicFranchiseRepository
                .findAllByTopic_IdOrderByPrimaryDescCreatedAtAsc(topicId);
        List<TopicArticle> topicArticles = topicArticleRepository
                .findAllByTopic_IdOrderByCreatedAtAsc(topicId);

        return TopicAnalysisDto.ContextResponse.from(topic, topicGames, topicFranchises, topicArticles);
    }

    @Transactional
    public TopicDto.TopicResponse updateAnalysis(
            Long topicId,
            TopicAnalysisDto.AnalysisUpdateRequest request) {
        Topic topic = findTopicById(topicId);
        topic.updateAnalysis(
                request.getTitle().trim(),
                request.getSummary().trim(),
                request.getCategory(),
                request.getImportanceScore(),
                request.getWhyImportant().trim()
        );
        Topic saved = topicRepository.saveAndFlush(topic);

        return TopicDto.TopicResponse.from(saved);
    }

    private Topic findTopicById(Long topicId) {
        return topicRepository.findById(topicId)
                .orElseThrow(() -> new IllegalArgumentException("Topic을 찾을 수 없습니다: " + topicId));
    }
}
