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
import com.gamenews.news.repository.TopicArticleRepository;
import com.gamenews.news.repository.TopicFranchiseRepository;
import com.gamenews.news.repository.TopicGameRepository;
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
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
    private final TopicArticleRepository topicArticleRepository;
    private final TopicGameRepository topicGameRepository;
    private final TopicFranchiseRepository topicFranchiseRepository;
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
        List<IgdbClient.IgdbFranchise> igdbFranchiseCandidates = mergeFranchiseCandidates(
                searchFranchises(request.getDetectedName()),
                deriveFranchisesFromGames(igdbCandidates));
        candidates.addAll(franchiseCandidates(
                findExactFranchiseCandidates(request.getDetectedName()),
                igdbFranchiseCandidates));
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

        List<IgdbClient.IgdbGame> igdbGameCandidates = searchGames(request.getDetectedName());
        List<IgdbClient.IgdbFranchise> igdbCandidates = mergeFranchiseCandidates(
                searchFranchises(request.getDetectedName()),
                deriveFranchisesFromGames(igdbGameCandidates));

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
                igdbGameCandidates));
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

    @Transactional
    public EntityReviewDto.AdminResponse reopenAdmin(Long reviewId) {
        EntityReview review = findReview(reviewId);
        if (review.getStatus() == EntityReviewStatus.PENDING) {
            return toAdminResponse(review);
        }

        Long topicId = rollbackResolvedRelation(review);
        List<EntityReviewDto.Candidate> refreshedCandidates = buildCandidatesForReview(review);
        review.reopen(writeCandidates(refreshedCandidates));
        EntityReview saved = entityReviewRepository.save(review);

        if (topicId != null) {
            eventPublisher.publishEvent(new EntityReviewResolvedEvent(topicId));
        }
        return toAdminResponse(saved);
    }

    private Game findSafeGame(
            EntityReviewDto.InternalResolveRequest request,
            List<Game> localCandidates,
            List<IgdbClient.IgdbGame> igdbCandidates) {
        IgdbClient.IgdbGame preferred = preferredExactGame(request, igdbCandidates);
        if (preferred != null) {
            Game mapped = gameRepository.findByIgdbId(preferred.getId()).orElse(null);
            if (mapped != null) {
                return mapped;
            }

            // A single legacy/local identity without IGDB metadata can safely absorb the
            // preferred official record. Multiple unmapped local rows remain an admin decision.
            if (localCandidates.size() == 1 && localCandidates.get(0).getIgdbId() == null) {
                Game local = localCandidates.get(0);
                try {
                    gameEnrichmentService.applyRawSnapshot(local, preferred);
                    return local;
                } catch (IllegalArgumentException ex) {
                    log.info("[EntityReview] Game auto-match rejected by catalog constraint - name={}, reason={}",
                            request.getDetectedName(), ex.getMessage());
                    return null;
                }
            }

            if (localCandidates.isEmpty()) {
                try {
                    return upsertIgdbGame(preferred);
                } catch (IllegalArgumentException ex) {
                    log.info("[EntityReview] Game auto-create rejected by catalog constraint - name={}, reason={}",
                            request.getDetectedName(), ex.getMessage());
                    return null;
                }
            }
            return null;
        }

        // IGDB lookup can be temporarily unavailable. A single already IGDB-backed local
        // identity is still safe to use; anything else goes to EntityReview.
        if (igdbCandidates.isEmpty() && localCandidates.size() == 1
                && localCandidates.get(0).getIgdbId() != null) {
            return localCandidates.get(0);
        }
        return null;
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

            Game mapped = gameRepository.findByIgdbId(raw.getId()).orElse(null);
            if (mapped != null) {
                return mapped;
            }

            List<Game> exactLocal = gameIdentityService.findExactCandidates(review.getDetectedName());
            List<Game> unmappedLocal = exactLocal.stream()
                    .filter(game -> game.getIgdbId() == null)
                    .toList();
            if (exactLocal.size() == 1 && unmappedLocal.size() == 1
                    && raw.getName() != null
                    && raw.getName().trim().equalsIgnoreCase(review.getDetectedName().trim())) {
                gameEnrichmentService.applyRawSnapshot(unmappedLocal.get(0), raw);
                return unmappedLocal.get(0);
            }

            return upsertIgdbGame(raw);
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

    private Game upsertIgdbGame(IgdbClient.IgdbGame raw) {
        Game game = gameRepository.findByIgdbId(raw.getId()).orElse(null);
        if (game == null) {
            game = Game.builder()
                    .name(raw.getName().trim())
                    .registrationSource(GameRegistrationSource.IGDB)
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

    private List<EntityReviewDto.Candidate> buildCandidatesForReview(EntityReview review) {
        String name = review.getDetectedName();
        List<IgdbClient.IgdbGame> igdbGames = searchGames(name);
        List<IgdbClient.IgdbFranchise> igdbFranchises = mergeFranchiseCandidates(
                searchFranchises(name),
                deriveFranchisesFromGames(igdbGames));

        List<EntityReviewDto.Candidate> candidates = new ArrayList<>();
        if (review.getEntityKind() == EntityReviewKind.FRANCHISE) {
            candidates.addAll(franchiseCandidates(findExactFranchiseCandidates(name), igdbFranchises));
            candidates.addAll(gameCandidates(gameIdentityService.findExactCandidates(name), igdbGames));
        } else {
            candidates.addAll(gameCandidates(gameIdentityService.findExactCandidates(name), igdbGames));
            candidates.addAll(franchiseCandidates(findExactFranchiseCandidates(name), igdbFranchises));
        }
        return candidates;
    }

    private Long rollbackResolvedRelation(EntityReview review) {
        Long articleId = review.getArticle().getId();
        Long topicId = topicArticleRepository.findByArticle_Id(articleId)
                .map(link -> link.getTopic().getId())
                .orElse(null);

        if (review.getResolvedGameId() != null) {
            Long gameId = review.getResolvedGameId();
            articleGameRepository.findByArticle_IdAndGame_Id(articleId, gameId)
                    .ifPresent(articleGameRepository::delete);
            if (topicId != null && !isGameStillUsedByTopic(topicId, gameId)) {
                topicGameRepository.findByTopic_IdAndGame_Id(topicId, gameId)
                        .ifPresent(topicGameRepository::delete);
            }
        }

        if (review.getResolvedFranchiseId() != null) {
            Long franchiseId = review.getResolvedFranchiseId();
            articleFranchiseRepository.findByArticle_IdAndFranchise_Id(articleId, franchiseId)
                    .ifPresent(articleFranchiseRepository::delete);
            if (topicId != null && !isFranchiseStillUsedByTopic(topicId, franchiseId)) {
                topicFranchiseRepository.findByTopic_IdAndFranchise_Id(topicId, franchiseId)
                        .ifPresent(topicFranchiseRepository::delete);
            }
        }
        return topicId;
    }

    private boolean isGameStillUsedByTopic(Long topicId, Long gameId) {
        return topicArticleRepository.findAllByTopic_IdOrderByCreatedAtAsc(topicId).stream()
                .anyMatch(link -> articleGameRepository.existsByArticle_IdAndGame_Id(
                        link.getArticle().getId(), gameId));
    }

    private boolean isFranchiseStillUsedByTopic(Long topicId, Long franchiseId) {
        return topicArticleRepository.findAllByTopic_IdOrderByCreatedAtAsc(topicId).stream()
                .anyMatch(link -> articleFranchiseRepository.existsByArticle_IdAndFranchise_Id(
                        link.getArticle().getId(), franchiseId));
    }

    private List<IgdbClient.IgdbFranchise> deriveFranchisesFromGames(List<IgdbClient.IgdbGame> games) {
        if (games == null || games.isEmpty() || !igdbClient.isConfigured()) return List.of();

        Set<Long> franchiseIds = new LinkedHashSet<>();
        for (IgdbClient.IgdbGame game : games) {
            if (game.getFranchise() != null && game.getFranchise().getId() != null) {
                franchiseIds.add(game.getFranchise().getId());
            }
            if (game.getFranchises() != null) {
                game.getFranchises().stream()
                        .filter(item -> item != null && item.getId() != null)
                        .map(IgdbClient.IgdbNamedEntity::getId)
                        .forEach(franchiseIds::add);
            }
        }
        if (franchiseIds.isEmpty()) return List.of();

        try {
            return igdbClient.getFranchisesByIds(new ArrayList<>(franchiseIds));
        } catch (RuntimeException ex) {
            log.warn("[EntityReview] IGDB derived Franchise lookup failed - ids={}, reason={}",
                    franchiseIds, ex.getMessage());
            return List.of();
        }
    }

    private List<IgdbClient.IgdbFranchise> mergeFranchiseCandidates(
            List<IgdbClient.IgdbFranchise> direct,
            List<IgdbClient.IgdbFranchise> derived) {
        Map<Long, IgdbClient.IgdbFranchise> unique = new LinkedHashMap<>();
        if (direct != null) {
            direct.stream().filter(item -> item != null && item.getId() != null)
                    .forEach(item -> unique.putIfAbsent(item.getId(), item));
        }
        if (derived != null) {
            derived.stream().filter(item -> item != null && item.getId() != null)
                    .forEach(item -> unique.putIfAbsent(item.getId(), item));
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

    private IgdbClient.IgdbGame preferredExactGame(
            EntityReviewDto.InternalResolveRequest request,
            List<IgdbClient.IgdbGame> candidates) {
        List<IgdbClient.IgdbGame> exact = candidates.stream()
                .filter(item -> item.getName() != null
                        && item.getName().trim().equalsIgnoreCase(request.getDetectedName().trim()))
                .toList();
        if (exact.size() == 1) {
            return exact.get(0);
        }
        if (exact.isEmpty()) {
            return null;
        }

        String variantIntent = explicitVariantIntent(request);
        if (variantIntent != null) {
            List<IgdbClient.IgdbGame> variantMatches = exact.stream()
                    .filter(item -> gameTypeMatches(item, variantIntent))
                    .toList();
            if (variantMatches.size() == 1) {
                return variantMatches.get(0);
            }
            // The article explicitly points at a variant, but IGDB still has multiple
            // matching records. Do not silently fall back to the main game.
            return null;
        }

        // General articles using the canonical title should resolve to the one official
        // Main Game when all other exact-name records are ports/remasters/variants.
        List<IgdbClient.IgdbGame> mainGames = exact.stream()
                .filter(this::isMainGame)
                .filter(item -> item.getVersionParent() == null)
                .toList();
        return mainGames.size() == 1 ? mainGames.get(0) : null;
    }

    private String explicitVariantIntent(EntityReviewDto.InternalResolveRequest request) {
        String text = ((request.getDetectedName() == null ? "" : request.getDetectedName()) + " "
                + (request.getReason() == null ? "" : request.getReason()))
                .toLowerCase(Locale.ROOT);
        if (containsAny(text, "remaster", "remastered", "리마스터")) return "remaster";
        if (containsAny(text, "remake", "리메이크")) return "remake";
        if (containsAny(text, "port", "ported", "포트", "이식")) return "port";
        if (containsAny(text, "expansion", "확장팩", "확장 콘텐츠")) return "expansion";
        if (containsAny(text, "dlc", "downloadable content", "다운로드 콘텐츠")) return "dlc";
        if (containsAny(text, "pack", "add-on", "addon", "애드온", "팩")) return "pack";
        return null;
    }

    private boolean gameTypeMatches(IgdbClient.IgdbGame game, String intent) {
        if (game.getGameType() == null || game.getGameType().getType() == null) return false;
        String type = game.getGameType().getType().toLowerCase(Locale.ROOT);
        return switch (intent) {
            case "remaster" -> type.contains("remaster");
            case "remake" -> type.contains("remake");
            case "port" -> type.contains("port");
            case "expansion" -> type.contains("expansion");
            case "dlc" -> type.contains("dlc");
            case "pack" -> type.contains("pack") || type.contains("addon") || type.contains("add-on");
            default -> false;
        };
    }

    private boolean isMainGame(IgdbClient.IgdbGame game) {
        return game.getGameType() != null
                && game.getGameType().getType() != null
                && game.getGameType().getType().equalsIgnoreCase("Main Game");
    }

    private boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle)) return true;
        }
        return false;
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
        Map<String, EntityReviewDto.Candidate> unique = new LinkedHashMap<>();
        localCandidates.forEach(game -> putCandidate(unique, EntityReviewDto.Candidate.builder()
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
        igdbCandidates.forEach(game -> putCandidate(unique, EntityReviewDto.Candidate.builder()
                .source("IGDB")
                .entityKind(EntityReviewKind.GAME)
                .igdbId(game.getId())
                .name(game.getName())
                .gameType(game.getGameType() == null ? null : game.getGameType().getType())
                .versionParentIgdbId(game.getVersionParent())
                .build()));
        return new ArrayList<>(unique.values());
    }

    private List<EntityReviewDto.Candidate> franchiseCandidates(
            List<Franchise> localCandidates,
            List<IgdbClient.IgdbFranchise> igdbCandidates) {
        Map<String, EntityReviewDto.Candidate> unique = new LinkedHashMap<>();
        localCandidates.forEach(franchise -> putCandidate(unique, EntityReviewDto.Candidate.builder()
                .source("LOCAL")
                .entityKind(EntityReviewKind.FRANCHISE)
                .localId(franchise.getId())
                .igdbId(franchise.getIgdbId())
                .name(franchise.getName())
                .displayName(franchise.getDisplayName())
                .build()));
        igdbCandidates.forEach(franchise -> putCandidate(unique, EntityReviewDto.Candidate.builder()
                .source("IGDB")
                .entityKind(EntityReviewKind.FRANCHISE)
                .igdbId(franchise.getId())
                .name(franchise.getName())
                .build()));
        return new ArrayList<>(unique.values());
    }

    private void putCandidate(
            Map<String, EntityReviewDto.Candidate> unique,
            EntityReviewDto.Candidate candidate) {
        String key = candidateIdentityKey(candidate);
        EntityReviewDto.Candidate existing = unique.get(key);
        if (existing == null) {
            unique.put(key, candidate);
            return;
        }

        // LOCAL catalog data and the same IGDB record represent one identity.
        // Prefer the richer local metadata while showing that IGDB verified the identity.
        EntityReviewDto.Candidate local = existing.getLocalId() != null ? existing
                : candidate.getLocalId() != null ? candidate : existing;
        EntityReviewDto.Candidate other = local == existing ? candidate : existing;
        unique.put(key, EntityReviewDto.Candidate.builder()
                .source(local.getLocalId() != null && local.getIgdbId() != null ? "LOCAL_IGDB" : local.getSource())
                .entityKind(local.getEntityKind())
                .localId(local.getLocalId())
                .igdbId(firstNonNull(local.getIgdbId(), other.getIgdbId()))
                .name(firstNonBlank(local.getName(), other.getName()))
                .displayName(firstNonBlank(local.getDisplayName(), other.getDisplayName()))
                .publisher(firstNonBlank(local.getPublisher(), other.getPublisher()))
                .developer(firstNonBlank(local.getDeveloper(), other.getDeveloper()))
                .gameType(firstNonBlank(local.getGameType(), other.getGameType()))
                .versionParentIgdbId(firstNonNull(local.getVersionParentIgdbId(), other.getVersionParentIgdbId()))
                .build());
    }

    private String candidateIdentityKey(EntityReviewDto.Candidate candidate) {
        if (candidate.getIgdbId() != null) {
            return candidate.getEntityKind() + ":IGDB:" + candidate.getIgdbId();
        }
        if (candidate.getLocalId() != null) {
            return candidate.getEntityKind() + ":LOCAL:" + candidate.getLocalId();
        }
        return candidate.getEntityKind() + ":NAME:"
                + String.valueOf(candidate.getName()).trim().toLowerCase(Locale.ROOT);
    }

    private <T> T firstNonNull(T first, T second) {
        return first != null ? first : second;
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) return first;
        return second;
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
