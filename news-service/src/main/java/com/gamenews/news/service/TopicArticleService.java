package com.gamenews.news.service;

import com.gamenews.news.dto.TopicArticleDto;
import com.gamenews.news.entity.NewsArticle;
import com.gamenews.news.entity.Topic;
import com.gamenews.news.entity.TopicArticle;
import com.gamenews.news.repository.NewsArticleRepository;
import com.gamenews.news.repository.TopicArticleRepository;
import com.gamenews.news.repository.TopicRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TopicArticleService {

    private final TopicArticleRepository topicArticleRepository;
    private final TopicRepository topicRepository;
    private final NewsArticleRepository newsArticleRepository;

    @Transactional
    public TopicArticleDto.TopicArticleResponse linkArticle(
            Long topicId,
            TopicArticleDto.CreateRequest request) {
        Topic topic = findTopicById(topicId);
        NewsArticle article = findArticleById(request.getArticleId());

        if (topicArticleRepository.existsByTopic_IdAndArticle_Id(topicId, request.getArticleId())) {
            throw new IllegalArgumentException("이미 Topic에 연결된 기사입니다: " + request.getArticleId());
        }

        TopicArticle topicArticle = TopicArticle.builder()
                .topic(topic)
                .article(article)
                .build();

        updateLastUpdatedAt(topic, article);

        return TopicArticleDto.TopicArticleResponse.from(topicArticleRepository.save(topicArticle));
    }

    public List<TopicArticleDto.TopicArticleResponse> getArticlesByTopic(Long topicId) {
        findTopicById(topicId);

        return topicArticleRepository.findAllByTopic_IdOrderByCreatedAtAsc(topicId).stream()
                .map(TopicArticleDto.TopicArticleResponse::from)
                .toList();
    }

    private void updateLastUpdatedAt(Topic topic, NewsArticle article) {
        LocalDateTime articleReferenceTime = article.getPublishedAt() != null
                ? article.getPublishedAt()
                : article.getCollectedAt();
        LocalDateTime currentLastUpdatedAt = topic.getLastUpdatedAt();

        if (currentLastUpdatedAt == null || articleReferenceTime.isAfter(currentLastUpdatedAt)) {
            topic.touch(articleReferenceTime);
        }
    }

    private Topic findTopicById(Long topicId) {
        return topicRepository.findById(topicId)
                .orElseThrow(() -> new IllegalArgumentException("Topic을 찾을 수 없습니다: " + topicId));
    }

    private NewsArticle findArticleById(Long articleId) {
        return newsArticleRepository.findById(articleId)
                .orElseThrow(() -> new IllegalArgumentException("기사를 찾을 수 없습니다: " + articleId));
    }
}
