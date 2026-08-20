package com.gamenews.news.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gamenews.news.client.IgdbClient;
import com.gamenews.news.dto.EntityReviewDto;
import com.gamenews.news.entity.ArticleFranchise;
import com.gamenews.news.entity.ArticleGame;
import com.gamenews.news.entity.EntityReview;
import com.gamenews.news.entity.EntityReviewKind;
import com.gamenews.news.entity.EntityReviewStatus;
import com.gamenews.news.entity.Franchise;
import com.gamenews.news.entity.Game;
import com.gamenews.news.entity.GameRegistrationSource;
import com.gamenews.news.entity.GameReviewStatus;
import com.gamenews.news.entity.NewsArticle;
import com.gamenews.news.event.EntityReviewResolvedEvent;
import com.gamenews.news.event.FranchiseResolvedEvent;
import com.gamenews.news.event.GameResolvedEvent;
import com.gamenews.news.repository.ArticleFranchiseRepository;
import com.gamenews.news.repository.ArticleGameRepository;
import com.gamenews.news.repository.EntityReviewRepository;
import com.gamenews.news.repository.FranchiseRepository;
import com.gamenews.news.repository.GameRepository;
import com.gamenews.news.repository.NewsArticleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class EntityReviewService {

    private static final int IGDB_CANDIDATE_LIMIT = 5;

    @Value("${app.entity-review.review-confidence-threshold:0.60}")
    private BigDecimal reviewThreshold;

    @Value("${app.entity-review.auto-confidence-threshold:0.90}")
    private BigDecimal autoThreshold;

    private final EntityReviewRepository entityReviewRepository;
    private final NewsArticleRepository newsArticleRepository;
    private final GameRepository gameRepository;
    private final FranchiseRepository franchiseRepository;
    private final ArticleGameRepository articleGameRepository;
    private final ArticleFranchiseRepository articleFranchiseRepository;
    private final GameIdentityService gameIdentityService;
    private final GameEnrichmentService gameEnrichmentService;
    private final FranchiseService franchiseService;
    private final IgdbClient igdbClient;
    private final TopicIntegrationService topicIntegrationService;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public EntityReviewDto.InternalResolveResponse resolveGame(EntityReviewDto.InternalResolveRequest request) {
        NewsArticle article = findArticle(request.getArticleId());
        if (request.getConfidenceScore().compareTo(reviewThreshold) < 0) {
            return ignored();
        }

        List<Game> localCandidates = gameIdentityService.findExactCandidates(request.getDetectedName());
        if (request.getConfidenceScore().compareTo(autoThreshold) >= 0
                && localCandidates.size() == 1
                && localCandidates.get(0).getIgdbId() != null) {
            Game safe = localCandidates.get(0);
            linkGame(article, safe, request);
            closePendingReview(article.getId(), EntityReviewKind.GAME, request.getDetectedName(), safe.getId(), null);
            eventPublisher.publishEvent(new GameResolvedEvent(safe.getId()));
            return EntityReviewDto.InternalResolveResponse.builder()
                    .outcome(EntityReviewDto.ResolutionOutcome.AUTO_LINKED)
                    .gameId(safe.getId())
                    .build();
        }

        List<IgdbClient.IgdbGame> igdbCandidates = searchGames(request.getDetectedName());

        if (request.getConfidenceScore().compareTo(autoThreshold) >= 0) {
            Game safe = findSafeGame(request, localCandidates, igdbCandidates);
            if (safe != null) {
                linkGame(article, safe, request);
                closePendingReview(article.getId(), EntityReviewKind.GAME, request.getDetectedName(), safe.getId(), null);
                eventPublisher.publishEvent(new GameResolvedEvent(safe.getId()));
                return EntityReviewDto.InternalResolveResponse.builder()
                        .outcome(EntityReviewDto.ResolutionOutcome.AUTO_LINKED)
                        .gameId(safe.getId())
                        .build();
            }
        }

        List<EntityReviewDto.Candidate> candidates = new ArrayList<>(gameCandidates(localCandidates, igdbCandidates));
        candidates.addAll(franchiseCandidates(
                findExactFranchiseCandidates(request.getDetectedName()),
                searchFranchises(request.getDetectedName())));
        EntityReview review = createOrRefreshReview(article, EntityReviewKind.GAME, request, candidates);
        return EntityReviewDto.InternalResolveResponse.builder()
                .outcome(EntityReviewDto.ResolutionOutcome.REVIEW_REQUIRED)
                .reviewId(review.getId())
                .build();
    }

    @Transactional
    public EntityReviewDto.InternalResolveResponse resolveFranchise(EntityReviewDto.InternalResolveRequest request) {
        NewsArticle article = findArticle(request.getArticleId());
        if (request.getConfidenceScore().compareTo(reviewThreshold) < 0) {
            return ignored();
        }

        List<Franchise> localCandidates = findExactFranchiseCandidates(request.getDetectedName());
        if (request.getConfidenceScore().compareTo(autoThreshold) >= 0
                && localCandidates.size() == 1
                && localCandidates.get(0).getIgdbId() != null) {
            Franchise safe = localCandidates.get(0);
            linkFranchise(article, safe, request);
            closePendingReview(article.getId(), EntityReviewKind.FRANCHISE, request.getDetectedName(), null, safe.getId());
            eventPublisher.publishEvent(new FranchiseResolvedEvent(safe.getId()));
            return EntityReviewDto.InternalResolveResponse.builder()
                    .outcome(EntityReviewDto.ResolutionOutcome.AUTO_LINKED)
                    .franchiseId(safe.getId())
                    .build();
        }

        List<IgdbClient.IgdbFranchise> igdbCandidates = searchFranchises(request.getDetectedName());

        if (request.getConfidenceScore().compareTo(autoThreshold) >= 0) {
            Franchise safe = findSafeFranchise(request.getDetectedName(), localCandidates, igdbCandidates);
            if (safe != null) {
                linkFranchise(article, safe, request);
                closePendingReview(article.getId(), EntityReviewKind.FRANCHISE, request.getDetectedName(), null, safe.getId());
                eventPublisher.publishEvent(new FranchiseResolvedEvent(safe.getId()));
                return EntityReviewDto.InternalResolveResponse.builder()
                        .outcome(EntityReviewDto.ResolutionOutcome.AUTO_LINKED)
                        .franchiseId(safe.getId())
                        .build();
            }
        }

        List<EntityReviewDto.Candidate> candidates = new ArrayList<>(franchiseCandidates(localCandidates, igdbCandidates));
        candidates.addAll(gameCandidates(
                gameIdentityService.findExactCandidates(request.getDetectedName()),
                searchGames(request.getDetectedName())));
        EntityReview review = createOrRefreshReview(article, EntityReviewKind.FRANCHISE, request, candidates);
        return EntityReviewDto.InternalResolveResponse.builder()
                .outcome(EntityReviewDto.ResolutionOutcome.REVIEW_REQUIRED)
                .reviewId(review.getId())
                .build();
    }

    public List<EntityReviewDto.AdminResponse> getAdminReviews(EntityReviewStatus status) {
        List<EntityReview> reviews = status == null
                ? entityReviewRepository.findAllByOrderByCreatedAtDesc()
                : entityReviewRepository.findAllByStatusOrderByCreatedAtDesc(status);
        return reviews.stream().map(this::toAdminResponse).toList();
    }

    public EntityReviewDto.AdminResponse getAdminReview(Long reviewId) {
        return toAdminResponse(findReview(reviewId));
    }

    @Transactional
    public EntityReviewDto.AdminResponse resolveAdmin(
            Long reviewId,
            EntityReviewDto.AdminResolveRequest request) {
        EntityReview review = findReview(reviewId);
        if (review.getStatus() != EntityReviewStatus.PENDING) {
            throw new IllegalArgumentException("이미 처리된 검토 항목입니다: " + reviewId);
        }

        if (request.getResolutionType() == EntityReviewDto.ResolutionType.UNRELATED) {
            review.reject();
            return toAdminResponse(entityReviewRepository.save(review));
        }

        if (request.getResolutionType() == EntityReviewDto.ResolutionType.GAME) {
            Game game = resolveAdminGame(review, request);
            linkGame(review.getArticle(), game, review);
            review.resolveGame(game.getId());
            eventPublisher.publishEvent(new GameResolvedEvent(game.getId()));
        } else if (request.getResolutionType() == EntityReviewDto.ResolutionType.FRANCHISE) {
            Franchise franchise = resolveAdminFranchise(request);
            linkFranchise(review.getArticle(), franchise, review);
            review.resolveFranchise(franchise.getId());
            eventPublisher.publishEvent(new FranchiseResolvedEvent(franchise.getId()));
        } else {
            throw new IllegalArgumentException("지원하지 않는 검토 결정입니다");
        }

        EntityReview saved = entityReviewRepository.save(review);
        Long topicId = topicIntegrationService.refreshRelationsForArticle(review.getArticle().getId());
        if (topicId != null) {
            eventPublisher.publishEvent(new EntityReviewResolvedEvent(topicId));
        }
        return toAdminResponse(saved);
    }

    private Game findSafeGame(
            EntityReviewDto.InternalResolveRequest request,
            List<Game> localCandidates,
            List<IgdbClient.IgdbGame> igdbCandidates) {
        if (localCandidates.size() > 1) return null;

        if (localCandidates.size() == 1) {
            Game local = localCandidates.get(0);
            if (local.getIgdbId() != null) return local;

            IgdbClient.IgdbGame exact = uniqueExactGame(request.getDetectedName(), igdbCandidates);
            if (exact == null) return null;
            Game alreadyMapped = gameRepository.findByIgdbId(exact.getId()).orElse(null);
            if (alreadyMapped != null && !alreadyMapped.getId().equals(local.getId())) return null;
            try {
                gameEnrichmentService.applyRawSnapshot(local, exact);
                return local;
            } catch (IllegalArgumentException ex) {
                log.info("[EntityReview] Game auto-match rejected by catalog constraint - name={}, reason={}",
                        request.getDetectedName(), ex.getMessage());
                return null;
            }
        }

        IgdbClient.IgdbGame exact = uniqueExactGame(request.getDetectedName(), igdbCandidates);
        if (exact == null) return null;
        try {
            return upsertIgdbGame(exact, request);
        } catch (IllegalArgumentException ex) {
            log.info("[EntityReview] Game auto-create rejected by catalog constraint - name={}, reason={}",
                    request.getDetectedName(), ex.getMessage());
            return null;
        }
    }

    private Franchise findSafeFranchise(
            String detectedName,
            List<Franchise> localCandidates,
            List<IgdbClient.IgdbFranchise> igdbCandidates) {
        if (localCandidates.size() > 1) return null;

        if (localCandidates.size() == 1) {
            Franchise local = localCandidates.get(0);
            if (local.getIgdbId() != null) return local;

            IgdbClient.IgdbFranchise exact = uniqueExactFranchise(local.getName(), igdbCandidates);
            if (exact == null) return null;
            try {
                return franchiseService.upsertIgdbFranchise(exact.getId(), exact.getName());
            } catch (IllegalArgumentException ex) {
                log.info("[EntityReview] Franchise auto-match rejected by catalog constraint - name={}, reason={}",
                        detectedName, ex.getMessage());
                return null;
            }
        }

        if (igdbCandidates.isEmpty()) return null;
        IgdbClient.IgdbFranchise exact = uniqueExactFranchise(detectedName, igdbCandidates);
        if (exact == null) return null;
        try {
            return franchiseService.upsertIgdbFranchise(exact.getId(), exact.getName());
        } catch (IllegalArgumentException ex) {
            log.info("[EntityReview] Franchise auto-create rejected by catalog constraint - name={}, reason={}",
                    detectedName, ex.getMessage());
            return null;
        }
    }

    private Game resolveAdminGame(EntityReview review, EntityReviewDto.AdminResolveRequest request) {
        if (request.getLocalEntityId() != null) {
            return gameRepository.findById(request.getLocalEntityId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "게임을 찾을 수 없습니다: " + request.getLocalEntityId()));
        }
        if (request.getIgdbId() != null) {
            IgdbClient.IgdbGame raw = igdbClient.getGameById(request.getIgdbId());
            EntityReviewDto.InternalResolveRequest context = EntityReviewDto.InternalResolveRequest.builder()
                    .articleId(review.getArticle().getId())
                    .detectedName(review.getDetectedName())
                    .entityType(review.getAiEntityType())
                    .primary(review.isPrimary())
                    .confidenceScore(review.getConfidenceScore())
                    .reason(review.getReason())
                    .build();
            return upsertIgdbGame(raw, context);
        }
        throw new IllegalArgumentException("Game 결정에는 localEntityId 또는 igdbId가 필요합니다");
    }

    private Franchise resolveAdminFranchise(EntityReviewDto.AdminResolveRequest request) {
        if (request.getLocalEntityId() != null) {
            return franchiseRepository.findById(request.getLocalEntityId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "프랜차이즈를 찾을 수 없습니다: " + request.getLocalEntityId()));
        }
        if (request.getIgdbId() != null) {
            IgdbClient.IgdbFranchise raw = igdbClient.getFranchiseById(request.getIgdbId());
            return franchiseService.upsertIgdbFranchise(raw.getId(), raw.getName());
        }
        throw new IllegalArgumentException("Franchise 결정에는 localEntityId 또는 igdbId가 필요합니다");
    }

    private Game upsertIgdbGame(IgdbClient.IgdbGame raw, EntityReviewDto.InternalResolveRequest request) {
        Game game = gameRepository.findByIgdbId(raw.getId()).orElse(null);
        if (game == null) {
            game = Game.builder()
                    .name(raw.getName().trim())
                    .registrationSource(GameRegistrationSource.IGDB)
                    .reviewStatus(GameReviewStatus.CONFIRMED)
                    .registrationConfidence(request.getConfidenceScore())
                    .sourceArticleId(request.getArticleId())
                    .build();
            game = gameRepository.save(game);
        }
        gameEnrichmentService.applyRawSnapshot(game, raw);
        return game;
    }

    private void linkGame(NewsArticle article, Game game, EntityReviewDto.InternalResolveRequest request) {
        if (articleGameRepository.existsByArticle_IdAndGame_Id(article.getId(), game.getId())) return;
        articleGameRepository.save(ArticleGame.builder()
                .article(article)
                .game(game)
                .primary(request.isPrimary())
                .confidenceScore(request.getConfidenceScore())
                .relevanceReason(trimToNull(request.getReason()))
                .build());
    }

    private void linkGame(NewsArticle article, Game game, EntityReview review) {
        if (articleGameRepository.existsByArticle_IdAndGame_Id(article.getId(), game.getId())) return;
        articleGameRepository.save(ArticleGame.builder()
                .article(article)
                .game(game)
                .primary(review.isPrimary())
                .confidenceScore(review.getConfidenceScore())
                .relevanceReason(trimToNull(review.getReason()))
                .build());
    }

    private void linkFranchise(NewsArticle article, Franchise franchise, EntityReviewDto.InternalResolveRequest request) {
        if (articleFranchiseRepository.existsByArticle_IdAndFranchise_Id(article.getId(), franchise.getId())) return;
        articleFranchiseRepository.save(ArticleFranchise.builder()
                .article(article)
                .franchise(franchise)
                .primary(request.isPrimary())
                .confidenceScore(request.getConfidenceScore())
                .relevanceReason(trimToNull(request.getReason()))
                .build());
    }

    private void linkFranchise(NewsArticle article, Franchise franchise, EntityReview review) {
        if (articleFranchiseRepository.existsByArticle_IdAndFranchise_Id(article.getId(), franchise.getId())) return;
        articleFranchiseRepository.save(ArticleFranchise.builder()
                .article(article)
                .franchise(franchise)
                .primary(review.isPrimary())
                .confidenceScore(review.getConfidenceScore())
                .relevanceReason(trimToNull(review.getReason()))
                .build());
    }

    private EntityReview createOrRefreshReview(
            NewsArticle article,
            EntityReviewKind kind,
            EntityReviewDto.InternalResolveRequest request,
            List<EntityReviewDto.Candidate> candidates) {
        String candidateJson = writeCandidates(candidates);
        EntityReview review = entityReviewRepository
                .findFirstByArticle_IdAndEntityKindAndDetectedNameIgnoreCaseAndStatusOrderByIdDesc(
                        article.getId(), kind, request.getDetectedName().trim(), EntityReviewStatus.PENDING)
                .orElseGet(() -> EntityReview.builder()
                        .article(article)
                        .entityKind(kind)
                        .detectedName(request.getDetectedName().trim())
                        .primary(request.isPrimary())
                        .status(EntityReviewStatus.PENDING)
                        .build());
        review.refresh(
                request.getEntityType(),
                request.isPrimary(),
                request.getConfidenceScore(),
                request.getReason(),
                candidateJson);
        EntityReview saved = entityReviewRepository.save(review);
        log.info("[EntityReview] Pending - reviewId={}, articleId={}, kind={}, name={}, confidence={}",
                saved.getId(), article.getId(), kind, saved.getDetectedName(), saved.getConfidenceScore());
        return saved;
    }

    private void closePendingReview(
            Long articleId,
            EntityReviewKind kind,
            String detectedName,
            Long gameId,
            Long franchiseId) {
        entityReviewRepository
                .findFirstByArticle_IdAndEntityKindAndDetectedNameIgnoreCaseAndStatusOrderByIdDesc(
                        articleId, kind, detectedName.trim(), EntityReviewStatus.PENDING)
                .ifPresent(review -> {
                    if (gameId != null) review.resolveGame(gameId);
                    else if (franchiseId != null) review.resolveFranchise(franchiseId);
                    entityReviewRepository.save(review);
                });
    }

    private List<Franchise> findExactFranchiseCandidates(String value) {
        if (value == null || value.isBlank()) return List.of();
        String key = value.trim().toLowerCase(Locale.ROOT);
        Map<Long, Franchise> unique = new LinkedHashMap<>();
        for (Franchise franchise : franchiseRepository.findAll()) {
            boolean matches = franchise.getName().trim().toLowerCase(Locale.ROOT).equals(key)
                    || (franchise.getDisplayName() != null
                    && franchise.getDisplayName().trim().toLowerCase(Locale.ROOT).equals(key))
                    || franchise.getAliases().stream()
                    .anyMatch(alias -> alias.getAlias().trim().toLowerCase(Locale.ROOT).equals(key));
            if (matches) unique.put(franchise.getId(), franchise);
        }
        return new ArrayList<>(unique.values());
    }

    private List<IgdbClient.IgdbGame> searchGames(String name) {
        if (!igdbClient.isConfigured()) return List.of();
        try {
            return igdbClient.searchGames(name, IGDB_CANDIDATE_LIMIT);
        } catch (RuntimeException ex) {
            log.warn("[EntityReview] IGDB Game candidate lookup failed - name={}, reason={}",
                    name, ex.getMessage());
            return List.of();
        }
    }

    private List<IgdbClient.IgdbFranchise> searchFranchises(String name) {
        if (!igdbClient.isConfigured()) return List.of();
        try {
            return igdbClient.searchFranchises(name, IGDB_CANDIDATE_LIMIT);
        } catch (RuntimeException ex) {
            log.warn("[EntityReview] IGDB Franchise candidate lookup failed - name={}, reason={}",
                    name, ex.getMessage());
            return List.of();
        }
    }

    private IgdbClient.IgdbGame uniqueExactGame(String detectedName, List<IgdbClient.IgdbGame> candidates) {
        List<IgdbClient.IgdbGame> exact = candidates.stream()
                .filter(item -> item.getName() != null && item.getName().trim().equalsIgnoreCase(detectedName.trim()))
                .toList();
        return exact.size() == 1 ? exact.get(0) : null;
    }

    private IgdbClient.IgdbFranchise uniqueExactFranchise(
            String preferredName,
            List<IgdbClient.IgdbFranchise> candidates) {
        if (candidates.isEmpty()) return null;
        String target = preferredName;
        if (target == null) {
            // Caller already searched by the detected name. Only a single result is safe without a local identity.
            return candidates.size() == 1 ? candidates.get(0) : null;
        }
        List<IgdbClient.IgdbFranchise> exact = candidates.stream()
                .filter(item -> item.getName() != null && item.getName().trim().equalsIgnoreCase(target.trim()))
                .toList();
        return exact.size() == 1 ? exact.get(0) : null;
    }

    private List<EntityReviewDto.Candidate> gameCandidates(
            List<Game> localCandidates,
            List<IgdbClient.IgdbGame> igdbCandidates) {
        List<EntityReviewDto.Candidate> result = new ArrayList<>();
        localCandidates.forEach(game -> result.add(EntityReviewDto.Candidate.builder()
                .source("LOCAL")
                .entityKind(EntityReviewKind.GAME)
                .localId(game.getId())
                .igdbId(game.getIgdbId())
                .name(game.getName())
                .displayName(game.getDisplayName())
                .publisher(game.getPublisher())
                .developer(game.getDeveloper())
                .gameType(game.getIgdbGameType())
                .versionParentIgdbId(game.getVersionParentIgdbId())
                .build()));
        igdbCandidates.forEach(game -> result.add(EntityReviewDto.Candidate.builder()
                .source("IGDB")
                .entityKind(EntityReviewKind.GAME)
                .igdbId(game.getId())
                .name(game.getName())
                .gameType(game.getGameType() == null ? null : game.getGameType().getType())
                .versionParentIgdbId(game.getVersionParent())
                .build()));
        return result;
    }

    private List<EntityReviewDto.Candidate> franchiseCandidates(
            List<Franchise> localCandidates,
            List<IgdbClient.IgdbFranchise> igdbCandidates) {
        List<EntityReviewDto.Candidate> result = new ArrayList<>();
        localCandidates.forEach(franchise -> result.add(EntityReviewDto.Candidate.builder()
                .source("LOCAL")
                .entityKind(EntityReviewKind.FRANCHISE)
                .localId(franchise.getId())
                .igdbId(franchise.getIgdbId())
                .name(franchise.getName())
                .displayName(franchise.getDisplayName())
                .build()));
        igdbCandidates.forEach(franchise -> result.add(EntityReviewDto.Candidate.builder()
                .source("IGDB")
                .entityKind(EntityReviewKind.FRANCHISE)
                .igdbId(franchise.getId())
                .name(franchise.getName())
                .build()));
        return result;
    }

    private EntityReviewDto.InternalResolveResponse ignored() {
        return EntityReviewDto.InternalResolveResponse.builder()
                .outcome(EntityReviewDto.ResolutionOutcome.IGNORED)
                .build();
    }

    private EntityReviewDto.AdminResponse toAdminResponse(EntityReview review) {
        return EntityReviewDto.AdminResponse.from(review, readCandidates(review.getCandidateJson()));
    }

    private String writeCandidates(List<EntityReviewDto.Candidate> candidates) {
        try {
            return objectMapper.writeValueAsString(candidates == null ? List.of() : candidates);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("검토 후보 직렬화에 실패했습니다", ex);
        }
    }

    private List<EntityReviewDto.Candidate> readCandidates(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<EntityReviewDto.Candidate>>() {});
        } catch (JsonProcessingException ex) {
            log.warn("[EntityReview] Candidate JSON parse failed - reason={}", ex.getMessage());
            return List.of();
        }
    }

    private EntityReview findReview(Long id) {
        return entityReviewRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("검토 항목을 찾을 수 없습니다: " + id));
    }

    private NewsArticle findArticle(Long id) {
        return newsArticleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("기사를 찾을 수 없습니다: " + id));
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
