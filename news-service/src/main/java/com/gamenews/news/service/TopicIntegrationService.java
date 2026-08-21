package com.gamenews.news.service;

import com.gamenews.news.dto.TopicIntegrationDto;
import com.gamenews.news.entity.ArticleFranchise;
import com.gamenews.news.entity.ArticleGame;
import com.gamenews.news.entity.NewsArticle;
import com.gamenews.news.entity.Topic;
import com.gamenews.news.entity.TopicArticle;
import com.gamenews.news.entity.TopicGame;
import com.gamenews.news.entity.TopicFranchise;
import com.gamenews.news.repository.ArticleFranchiseRepository;
import com.gamenews.news.repository.ArticleGameRepository;
import com.gamenews.news.repository.NewsArticleRepository;
import com.gamenews.news.repository.TopicArticleRepository;
import com.gamenews.news.repository.TopicGameRepository;
import com.gamenews.news.repository.TopicFranchiseRepository;
import com.gamenews.news.repository.TopicRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TopicIntegrationService {

    private final TopicRepository topicRepository;
    private final TopicArticleRepository topicArticleRepository;
    private final TopicGameRepository topicGameRepository;
    private final TopicFranchiseRepository topicFranchiseRepository;
    private final NewsArticleRepository newsArticleRepository;
    private final ArticleGameRepository articleGameRepository;
    private final ArticleFranchiseRepository articleFranchiseRepository;

    public List<TopicIntegrationDto.CandidateResponse> getCandidates(
            TopicIntegrationDto.CandidateRequest request) {
        NewsArticle article = findArticleById(request.getArticleId());
        List<ArticleGame> articleGames = articleGameRepository
                .findAllByArticle_IdOrderByPrimaryDescCreatedAtAsc(article.getId());
        List<ArticleFranchise> articleFranchises = articleFranchiseRepository
                .findAllByArticle_IdOrderByPrimaryDescCreatedAtAsc(article.getId());

        LocalDateTime referenceTime = article.getPublishedAt() != null
                ? article.getPublishedAt()
                : article.getCollectedAt();
        LocalDateTime cutoff = referenceTime.minusHours(request.getWindowHours());

        List<Topic> candidates = new ArrayList<>();
        Set<Long> candidateIds = new LinkedHashSet<>();

        if (!articleGames.isEmpty()) {
            List<Long> gameIds = articleGames.stream()
                    .map(articleGame -> articleGame.getGame().getId())
                    .distinct()
                    .toList();

            List<Topic> gameCandidates = topicRepository
                    .findCandidatesByGameIdsAndUpdatedAfter(
                            gameIds,
                            cutoff,
                            PageRequest.of(0, request.getLimit()));

            for (Topic topic : gameCandidates) {
                if (candidateIds.add(topic.getId())) {
                    candidates.add(topic);
                }
            }
        }

        if (!articleFranchises.isEmpty() && candidates.size() < request.getLimit()) {
            List<Long> franchiseIds = articleFranchises.stream()
                    .map(articleFranchise -> articleFranchise.getFranchise().getId())
                    .distinct()
                    .toList();

            List<Topic> franchiseCandidates = topicRepository
                    .findCandidatesByFranchiseIdsAndUpdatedAfter(
                            franchiseIds,
                            cutoff,
                            PageRequest.of(0, request.getLimit()));

            for (Topic topic : franchiseCandidates) {
                if (candidates.size() >= request.getLimit()) {
                    break;
                }
                if (candidateIds.add(topic.getId())) {
                    candidates.add(topic);
                }
            }
        }

        if (request.isAllowRecentFallback() && candidates.size() < request.getLimit()) {
            List<Topic> recentCandidates = topicRepository
                    .findRecentCandidatesUpdatedAfter(
                            cutoff,
                            PageRequest.of(0, request.getLimit()));

            for (Topic topic : recentCandidates) {
                if (candidates.size() >= request.getLimit()) {
                    break;
                }
                if (candidateIds.add(topic.getId())) {
                    candidates.add(topic);
                }
            }
        }

        return candidates.stream()
                .map(TopicIntegrationDto.CandidateResponse::from)
                .toList();
    }

    public TopicIntegrationDto.IntegrateResponse getExistingIntegration(Long articleId) {
        return topicArticleRepository.findByArticle_Id(articleId)
                .map(link -> TopicIntegrationDto.IntegrateResponse.builder()
                        .topicId(link.getTopic().getId())
                        .action(TopicIntegrationDto.IntegrationAction.ALREADY_LINKED)
                        .build())
                .orElse(null);
    }

    @Transactional
    public TopicIntegrationDto.IntegrateResponse integrate(
            TopicIntegrationDto.IntegrateRequest request) {
        NewsArticle article = findArticleById(request.getArticleId());

        Optional<TopicArticle> existingLink = topicArticleRepository
                .findByArticle_Id(article.getId());
        if (existingLink.isPresent()) {
            Topic existingTopic = existingLink.get().getTopic();
            syncArticleGamesToTopic(existingTopic, article.getId());
            syncArticleFranchisesToTopic(existingTopic, article.getId());
            existingTopic.absorbArticleTime(getArticleReferenceTime(article));
            return TopicIntegrationDto.IntegrateResponse.builder()
                    .topicId(existingTopic.getId())
                    .action(TopicIntegrationDto.IntegrationAction.ALREADY_LINKED)
                    .build();
        }

        boolean createNew = request.getTargetTopicId() == null;
        Topic topic = createNew
                ? createTopicFromArticle(article, request)
                : findTopicById(request.getTargetTopicId());

        topicArticleRepository.save(TopicArticle.builder()
                .topic(topic)
                .article(article)
                .build());

        syncArticleGamesToTopic(topic, article.getId());
        syncArticleFranchisesToTopic(topic, article.getId());
        updateLastUpdatedAt(topic, article);

        return TopicIntegrationDto.IntegrateResponse.builder()
                .topicId(topic.getId())
                .action(createNew
                        ? TopicIntegrationDto.IntegrationAction.CREATED_NEW
                        : TopicIntegrationDto.IntegrationAction.LINKED_EXISTING)
                .build();
    }

    @Transactional
    public Long refreshRelationsForArticle(Long articleId) {
        NewsArticle article = findArticleById(articleId);
        Optional<TopicArticle> existingLink = topicArticleRepository
                .findByArticle_Id(articleId);
        if (existingLink.isEmpty()) {
            return null;
        }

        Topic topic = existingLink.get().getTopic();
        syncArticleGamesToTopic(topic, articleId);
        syncArticleFranchisesToTopic(topic, articleId);
        topic.absorbArticleTime(getArticleReferenceTime(article));
        return topic.getId();
    }

    private Topic createTopicFromArticle(
            NewsArticle article,
            TopicIntegrationDto.IntegrateRequest request) {
        String title = trimToNull(request.getTitle());
        if (title == null) {
            title = article.getTitle();
        }

        LocalDateTime firstSeenAt = getArticleReferenceTime(article);

        Topic topic = Topic.builder()
                .title(title)
                .summary(trimToNull(request.getSummary()))
                .whyImportant(trimToNull(request.getInitialWhyImportant()))
                .category(request.getCategory())
                .importanceScore(request.getInitialImportanceScore())
                .firstSeenAt(firstSeenAt)
                .lastUpdatedAt(firstSeenAt)
                .build();

        return topicRepository.save(topic);
    }

    private void syncArticleGamesToTopic(Topic topic, Long articleId) {
        List<ArticleGame> articleGames = articleGameRepository
                .findAllByArticle_IdOrderByPrimaryDescCreatedAtAsc(articleId);

        for (ArticleGame articleGame : articleGames) {
            Long gameId = articleGame.getGame().getId();
            TopicGame topicGame = topicGameRepository
                    .findByTopic_IdAndGame_Id(topic.getId(), gameId)
                    .orElseGet(() -> TopicGame.builder()
                            .topic(topic)
                            .game(articleGame.getGame())
                            .primary(articleGame.isPrimary())
                            .relevanceScore(articleGame.getConfidenceScore())
                            .build());
            topicGame.absorbMetadata(articleGame.isPrimary(), articleGame.getConfidenceScore());
            topicGameRepository.save(topicGame);
        }
    }

    private void syncArticleFranchisesToTopic(Topic topic, Long articleId) {
        List<ArticleFranchise> articleFranchises = articleFranchiseRepository
                .findAllByArticle_IdOrderByPrimaryDescCreatedAtAsc(articleId);

        for (ArticleFranchise articleFranchise : articleFranchises) {
            Long franchiseId = articleFranchise.getFranchise().getId();
            TopicFranchise topicFranchise = topicFranchiseRepository
                    .findByTopic_IdAndFranchise_Id(topic.getId(), franchiseId)
                    .orElseGet(() -> TopicFranchise.builder()
                            .topic(topic)
                            .franchise(articleFranchise.getFranchise())
                            .primary(articleFranchise.isPrimary())
                            .relevanceScore(articleFranchise.getConfidenceScore())
                            .build());

            topicFranchise.absorbMetadata(
                    articleFranchise.isPrimary(),
                    articleFranchise.getConfidenceScore());
            topicFranchiseRepository.save(topicFranchise);
        }
    }

    private void updateLastUpdatedAt(Topic topic, NewsArticle article) {
        topic.absorbArticleTime(getArticleReferenceTime(article));
    }

    private LocalDateTime getArticleReferenceTime(NewsArticle article) {
        return article.getPublishedAt() != null
                ? article.getPublishedAt()
                : article.getCollectedAt();
    }

    private NewsArticle findArticleById(Long articleId) {
        return newsArticleRepository.findById(articleId)
                .orElseThrow(() -> new IllegalArgumentException("기사를 찾을 수 없습니다: " + articleId));
    }

    private Topic findTopicById(Long topicId) {
        return topicRepository.findById(topicId)
                .orElseThrow(() -> new IllegalArgumentException("Topic을 찾을 수 없습니다: " + topicId));
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
