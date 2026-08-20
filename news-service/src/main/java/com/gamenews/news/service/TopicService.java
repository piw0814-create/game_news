package com.gamenews.news.service;

import com.gamenews.news.dto.TopicDto;
import com.gamenews.news.entity.Topic;
import com.gamenews.news.entity.TopicArticle;
import com.gamenews.news.entity.TopicGame;
import com.gamenews.news.entity.TopicFranchise;
import com.gamenews.news.repository.TopicArticleRepository;
import com.gamenews.news.repository.TopicCommentRepository;
import com.gamenews.news.repository.TopicGameRepository;
import com.gamenews.news.repository.TopicFranchiseRepository;
import com.gamenews.news.repository.TopicLikeRepository;
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

    private static final int LIKE_BONUS_CAP = 20;

    private final TopicRepository topicRepository;
    private final TopicArticleRepository topicArticleRepository;
    private final TopicGameRepository topicGameRepository;
    private final TopicFranchiseRepository topicFranchiseRepository;
    private final TopicLikeRepository topicLikeRepository;
    private final TopicCommentRepository topicCommentRepository;

    public List<TopicDto.TopicResponse> getAllTopics() {
        List<Topic> topics = topicRepository.findAllByOrderByLastUpdatedAtDesc();
        if (topics.isEmpty()) {
            return List.of();
        }

        List<Long> topicIds = topics.stream()
                .map(Topic::getId)
                .toList();

        Map<Long, List<TopicDto.GameSummary>> gamesByTopicId = topicGameRepository
                .findAllWithGameDetailsByTopicIds(topicIds)
                .stream()
                .collect(Collectors.groupingBy(
                        topicGame -> topicGame.getTopic().getId(),
                        Collectors.mapping(TopicDto.GameSummary::from, Collectors.toList())
                ));

        Map<Long, List<TopicDto.FranchiseSummary>> franchisesByTopicId = topicFranchiseRepository
                .findAllWithFranchiseDetailsByTopicIds(topicIds)
                .stream()
                .collect(Collectors.groupingBy(
                        topicFranchise -> topicFranchise.getTopic().getId(),
                        Collectors.mapping(TopicDto.FranchiseSummary::from, Collectors.toList())
                ));

        Map<Long, Long> likeCountByTopicId = topicLikeRepository.countByTopicIds(topicIds)
                .stream()
                .collect(Collectors.toMap(
                        TopicLikeRepository.TopicInteractionCountView::getTopicId,
                        TopicLikeRepository.TopicInteractionCountView::getCount
                ));

        Map<Long, Long> commentCountByTopicId = topicCommentRepository.countByTopicIds(topicIds)
                .stream()
                .collect(Collectors.toMap(
                        TopicCommentRepository.TopicInteractionCountView::getTopicId,
                        TopicCommentRepository.TopicInteractionCountView::getCount
                ));

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        return topics.stream()
                .map(topic -> {
                    long likeCount = likeCountByTopicId.getOrDefault(topic.getId(), 0L);
                    long commentCount = commentCountByTopicId.getOrDefault(topic.getId(), 0L);
                    int likeBonus = calculateLikeBonus(likeCount);

                    return TopicDto.TopicResponse.from(
                            topic,
                            gamesByTopicId.getOrDefault(topic.getId(), Collections.emptyList()),
                            franchisesByTopicId.getOrDefault(topic.getId(), Collections.emptyList()),
                            calculateRecencyBonus(topic.getLastUpdatedAt(), now),
                            calculateFinalImportance(topic.getImportanceScore(), likeBonus),
                            likeCount,
                            commentCount,
                            likeBonus
                    );
                })
                .toList();
    }

    public TopicDto.TopicDetailResponse getTopic(Long id) {
        Topic topic = findTopicById(id);
        List<TopicGame> topicGames = topicGameRepository
                .findAllByTopic_IdOrderByPrimaryDescCreatedAtAsc(id);
        List<TopicFranchise> topicFranchises = topicFranchiseRepository
                .findAllByTopic_IdOrderByPrimaryDescCreatedAtAsc(id);
        List<TopicArticle> topicArticles = topicArticleRepository
                .findAllByTopic_IdOrderByCreatedAtAsc(id);

        long likeCount = topicLikeRepository.countByTopic_Id(id);
        long commentCount = topicCommentRepository.countByTopic_Id(id);
        int likeBonus = calculateLikeBonus(likeCount);

        return TopicDto.TopicDetailResponse.from(
                topic,
                topicGames,
                topicFranchises,
                topicArticles,
                calculateFinalImportance(topic.getImportanceScore(), likeBonus),
                likeCount,
                commentCount,
                likeBonus
        );
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

    private int calculateLikeBonus(long likeCount) {
        return (int) Math.min(LIKE_BONUS_CAP, Math.max(0, likeCount));
    }

    private Integer calculateFinalImportance(Integer baseImportanceScore, int likeBonus) {
        if (baseImportanceScore == null) {
            return null;
        }
        return Math.max(0, Math.min(100, baseImportanceScore + likeBonus));
    }

}
