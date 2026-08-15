package com.gamenews.news.service;

import com.gamenews.news.dto.TopicDto;
import com.gamenews.news.entity.Topic;
import com.gamenews.news.entity.TopicArticle;
import com.gamenews.news.entity.TopicGame;
import com.gamenews.news.repository.TopicArticleRepository;
import com.gamenews.news.repository.TopicGameRepository;
import com.gamenews.news.repository.TopicRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TopicService {

    private final TopicRepository topicRepository;
    private final TopicArticleRepository topicArticleRepository;
    private final TopicGameRepository topicGameRepository;

    @Transactional
    public TopicDto.TopicResponse createTopic(TopicDto.CreateRequest request) {
        LocalDateTime now = LocalDateTime.now();

        Topic topic = Topic.builder()
                .title(request.getTitle().trim())
                .summary(trimToNull(request.getSummary()))
                .whyImportant(trimToNull(request.getWhyImportant()))
                .category(request.getCategory())
                .importanceScore(request.getImportanceScore())
                .firstSeenAt(request.getFirstSeenAt() != null ? request.getFirstSeenAt() : now)
                .lastUpdatedAt(now)
                .build();

        return TopicDto.TopicResponse.from(topicRepository.save(topic));
    }

    public List<TopicDto.TopicResponse> getAllTopics() {
        return topicRepository.findAllByOrderByLastUpdatedAtDesc().stream()
                .map(TopicDto.TopicResponse::from)
                .toList();
    }

    public TopicDto.TopicDetailResponse getTopic(Long id) {
        Topic topic = findTopicById(id);
        List<TopicGame> topicGames = topicGameRepository
                .findAllByTopic_IdOrderByPrimaryDescCreatedAtAsc(id);
        List<TopicArticle> topicArticles = topicArticleRepository
                .findAllByTopic_IdOrderByCreatedAtAsc(id);

        return TopicDto.TopicDetailResponse.from(topic, topicGames, topicArticles);
    }

    private Topic findTopicById(Long id) {
        return topicRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Topic을 찾을 수 없습니다: " + id));
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
