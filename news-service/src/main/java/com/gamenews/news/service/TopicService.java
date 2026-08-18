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

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TopicService {

    private final TopicRepository topicRepository;
    private final TopicArticleRepository topicArticleRepository;
    private final TopicGameRepository topicGameRepository;

    @Transactional
    public TopicDto.TopicResponse createTopic(TopicDto.CreateRequest request) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime firstSeenAt = request.getFirstSeenAt() != null
                ? request.getFirstSeenAt()
                : now;

        Topic topic = Topic.builder()
                .title(request.getTitle().trim())
                .summary(trimToNull(request.getSummary()))
                .whyImportant(trimToNull(request.getWhyImportant()))
                .category(request.getCategory())
                .importanceScore(request.getImportanceScore())
                .firstSeenAt(firstSeenAt)
                .lastUpdatedAt(firstSeenAt)
                .build();

        Topic saved = topicRepository.save(topic);
        return TopicDto.TopicResponse.from(saved, List.of(), calculateRecencyBonus(saved.getLastUpdatedAt(), now));
    }

    public List<TopicDto.TopicResponse> getAllTopics() {
        List<Topic> topics = topicRepository.findAllByOrderByLastUpdatedAtDesc();
        if (topics.isEmpty()) {
            return List.of();
        }

        List<Long> topicIds = topics.stream()
                .map(Topic::getId)
                .toList();

        Map<Long, List<Long>> gameIdsByTopicId = topicGameRepository.findGameIdsByTopicIds(topicIds)
                .stream()
                .collect(Collectors.groupingBy(
                        TopicGameRepository.TopicGameIdView::getTopicId,
                        Collectors.mapping(
                                TopicGameRepository.TopicGameIdView::getGameId,
                                Collectors.toList()
                        )
                ));

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        return topics.stream()
                .map(topic -> TopicDto.TopicResponse.from(
                        topic,
                        gameIdsByTopicId.getOrDefault(topic.getId(), Collections.emptyList()),
                        calculateRecencyBonus(topic.getLastUpdatedAt(), now)
                ))
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

    private int calculateRecencyBonus(LocalDateTime lastUpdatedAt, LocalDateTime now) {
        if (lastUpdatedAt == null) {
            return 0;
        }

        long hours = Math.max(0, Duration.between(lastUpdatedAt, now).toHours());

        if (hours <= 24) {
            return 10;
        }
        if (hours <= 72) {
            return 6;
        }
        if (hours <= 168) {
            return 3;
        }
        return 0;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
