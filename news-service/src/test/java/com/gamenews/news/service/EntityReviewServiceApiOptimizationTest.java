package com.gamenews.news.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gamenews.news.client.IgdbClient;
import com.gamenews.news.dto.EntityReviewDto;
import com.gamenews.news.entity.EntityReview;
import com.gamenews.news.entity.EntityReviewKind;
import com.gamenews.news.entity.EntityReviewStatus;
import com.gamenews.news.event.EntityReviewResolvedEvent;
import com.gamenews.news.entity.Franchise;
import com.gamenews.news.entity.Game;
import com.gamenews.news.entity.NewsArticle;
import com.gamenews.news.repository.ArticleFranchiseRepository;
import com.gamenews.news.repository.ArticleGameRepository;
import com.gamenews.news.repository.EntityReviewRepository;
import com.gamenews.news.repository.FranchiseRepository;
import com.gamenews.news.repository.GameRepository;
import com.gamenews.news.repository.NewsArticleRepository;
import com.gamenews.news.repository.TopicArticleRepository;
import com.gamenews.news.repository.TopicFranchiseRepository;
import com.gamenews.news.repository.TopicGameRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.AdditionalAnswers.returnsFirstArg;

@ExtendWith(MockitoExtension.class)
class EntityReviewServiceApiOptimizationTest {

    @Mock EntityReviewRepository entityReviewRepository;
    @Mock NewsArticleRepository newsArticleRepository;
    @Mock GameRepository gameRepository;
    @Mock FranchiseRepository franchiseRepository;
    @Mock ArticleGameRepository articleGameRepository;
    @Mock ArticleFranchiseRepository articleFranchiseRepository;
    @Mock TopicArticleRepository topicArticleRepository;
    @Mock TopicGameRepository topicGameRepository;
    @Mock TopicFranchiseRepository topicFranchiseRepository;
    @Mock GameIdentityService gameIdentityService;
    @Mock GameEnrichmentService gameEnrichmentService;
    @Mock FranchiseService franchiseService;
    @Mock IgdbClient igdbClient;
    @Spy EntityCandidateRankingService candidateRankingService = new EntityCandidateRankingService();
    @Mock TopicIntegrationService topicIntegrationService;
    @Mock ObjectMapper objectMapper;
    @Mock ApplicationEventPublisher eventPublisher;

    @InjectMocks EntityReviewService service;

    @BeforeEach
    void setThresholds() {
        ReflectionTestUtils.setField(service, "reviewThreshold", new BigDecimal("0.60"));
        ReflectionTestUtils.setField(service, "autoThreshold", new BigDecimal("0.90"));
    }

    @Test
    void verifiedLocalExactGameAutoLinksWithoutCallingIgdb() {
        NewsArticle article = org.mockito.Mockito.mock(NewsArticle.class);
        Game local = org.mockito.Mockito.mock(Game.class);
        when(article.getId()).thenReturn(10L);
        when(local.getId()).thenReturn(20L);
        when(local.getIgdbId()).thenReturn(30L);
        when(newsArticleRepository.findById(10L)).thenReturn(Optional.of(article));
        when(gameIdentityService.findExactCandidates("Dota 2")).thenReturn(List.of(local));
        when(articleGameRepository.existsByArticle_IdAndGame_Id(10L, 20L)).thenReturn(false);
        when(entityReviewRepository.findFirstByArticle_IdAndEntityKindAndDetectedNameIgnoreCaseAndStatusOrderByIdDesc(
                any(), any(), any(), any())).thenReturn(Optional.empty());

        EntityReviewDto.InternalResolveResponse response = service.resolveGame(request(10L, "Dota 2", "게임 본편 업데이트"));

        assertThat(response.getOutcome()).isEqualTo(EntityReviewDto.ResolutionOutcome.AUTO_LINKED);
        assertThat(response.getGameId()).isEqualTo(20L);
        verifyNoInteractions(igdbClient);
    }

    @Test
    void newExactIgdbGameDoesNotFallThroughToFuzzySearch() {
        NewsArticle article = org.mockito.Mockito.mock(NewsArticle.class);
        Game mapped = org.mockito.Mockito.mock(Game.class);
        IgdbClient.IgdbGame raw = new IgdbClient.IgdbGame();
        raw.setId(100L);
        raw.setName("The Finals");
        IgdbClient.IgdbGameType gameType = new IgdbClient.IgdbGameType();
        gameType.setType("Main Game");
        raw.setGameType(gameType);

        when(article.getId()).thenReturn(11L);
        when(mapped.getId()).thenReturn(21L);
        when(newsArticleRepository.findById(11L)).thenReturn(Optional.of(article));
        when(gameIdentityService.findExactCandidates("The Finals")).thenReturn(List.of());
        when(igdbClient.isConfigured()).thenReturn(true);
        when(igdbClient.findGamesByExactName("The Finals", 20)).thenReturn(List.of(raw));
        when(gameRepository.findByIgdbId(100L)).thenReturn(Optional.of(mapped));
        when(articleGameRepository.existsByArticle_IdAndGame_Id(11L, 21L)).thenReturn(false);
        when(entityReviewRepository.findFirstByArticle_IdAndEntityKindAndDetectedNameIgnoreCaseAndStatusOrderByIdDesc(
                any(), any(), any(), any())).thenReturn(Optional.empty());

        EntityReviewDto.InternalResolveResponse response = service.resolveGame(request(11L, "The Finals", "The Finals 업데이트"));

        assertThat(response.getOutcome()).isEqualTo(EntityReviewDto.ResolutionOutcome.AUTO_LINKED);
        assertThat(response.getGameId()).isEqualTo(21L);
        verify(igdbClient).findGamesByExactName("The Finals", 20);
        verify(igdbClient, never()).searchGames(any(), anyInt());
    }


    @Test
    void safeLeadingExpansionAutoLinksFc26WithoutFranchiseIgdbCalls() {
        NewsArticle article = org.mockito.Mockito.mock(NewsArticle.class);
        Game mapped = org.mockito.Mockito.mock(Game.class);
        when(article.getId()).thenReturn(15L);
        when(mapped.getId()).thenReturn(25L);
        when(mapped.getName()).thenReturn("EA Sports FC 26");
        when(newsArticleRepository.findById(15L)).thenReturn(Optional.of(article));
        when(gameIdentityService.findExactCandidates("FC 26")).thenReturn(List.of());
        when(igdbClient.isConfigured()).thenReturn(true);
        when(igdbClient.findGamesByExactName("FC 26", 20)).thenReturn(List.of());

        IgdbClient.IgdbGame quiz = game(396596L, "FC 26 Quiz", "Main Game");
        IgdbClient.IgdbGame canonical = game(353848L, "EA Sports FC 26", "Main Game");
        when(igdbClient.searchGames("FC 26", 5)).thenReturn(List.of(quiz, canonical));
        when(gameRepository.findByIgdbId(353848L)).thenReturn(Optional.of(mapped));
        when(articleGameRepository.existsByArticle_IdAndGame_Id(15L, 25L)).thenReturn(false);
        when(entityReviewRepository.findFirstByArticle_IdAndEntityKindAndDetectedNameIgnoreCaseAndStatusOrderByIdDesc(
                any(), any(), any(), any())).thenReturn(Optional.empty());

        EntityReviewDto.InternalResolveResponse response = service.resolveGame(
                request(15L, "FC 26", "EA Sports FC 26 FUTTIES update"));

        assertThat(response.getOutcome()).isEqualTo(EntityReviewDto.ResolutionOutcome.AUTO_LINKED);
        assertThat(response.getGameId()).isEqualTo(25L);
        verify(igdbClient).findGamesByExactName("FC 26", 20);
        verify(igdbClient).searchGames("FC 26", 5);
        verify(igdbClient, never()).findFranchisesByExactName(any(), anyInt());
        verify(igdbClient, never()).searchFranchises(any(), anyInt());
        verify(igdbClient, never()).findCollectionsByExactName(any(), anyInt());
        verify(igdbClient, never()).searchCollections(any(), anyInt());
        verify(mapped).addAlias("FC 26");
    }

    @Test
    void duplicateCanonicalNamesRemainReviewRequired() throws Exception {
        NewsArticle article = org.mockito.Mockito.mock(NewsArticle.class);
        when(article.getId()).thenReturn(48L);
        when(newsArticleRepository.findById(48L)).thenReturn(Optional.of(article));
        when(gameIdentityService.findExactCandidates("Zero Hour")).thenReturn(List.of());
        when(igdbClient.isConfigured()).thenReturn(true);
        when(igdbClient.findGamesByExactName("Zero Hour", 20)).thenReturn(List.of(
                game(39344L, "Zero Hour", "Main Game"),
                game(146042L, "Zero Hour", "Main Game"),
                game(151730L, "Zero Hour", "Main Game")));
        when(objectMapper.writeValueAsString(any())).thenReturn("[]");
        when(entityReviewRepository.findFirstByArticle_IdAndEntityKindAndDetectedNameIgnoreCaseAndStatusOrderByIdDesc(
                any(), any(), any(), any())).thenReturn(Optional.empty());
        when(entityReviewRepository.save(any(EntityReview.class))).then(returnsFirstArg());

        EntityReviewDto.InternalResolveResponse response = service.resolveGame(
                request(48L, "Zero Hour", "Zero Hour console release"));

        assertThat(response.getOutcome()).isEqualTo(EntityReviewDto.ResolutionOutcome.REVIEW_REQUIRED);
        verify(gameRepository, never()).findByIgdbId(any());
    }

    @Test
    void exactIgdbCollectionCanAutoResolveFranchiseIdentity() {
        NewsArticle article = org.mockito.Mockito.mock(NewsArticle.class);
        Franchise mapped = org.mockito.Mockito.mock(Franchise.class);
        when(article.getId()).thenReturn(57L);
        when(mapped.getId()).thenReturn(31L);
        when(newsArticleRepository.findById(57L)).thenReturn(Optional.of(article));
        when(franchiseRepository.findExactIdentityCandidates("Thief")).thenReturn(List.of());
        when(igdbClient.isConfigured()).thenReturn(true);
        when(igdbClient.findFranchisesByExactName("Thief", 20)).thenReturn(List.of());

        IgdbClient.IgdbCollection collection = new IgdbClient.IgdbCollection();
        collection.setId(2L);
        collection.setName("Thief");
        when(igdbClient.findCollectionsByExactName("Thief", 20)).thenReturn(List.of(collection));
        when(franchiseService.upsertIgdbCollection(2L, "Thief")).thenReturn(mapped);
        when(articleFranchiseRepository.existsByArticle_IdAndFranchise_Id(57L, 31L)).thenReturn(false);
        when(entityReviewRepository.findFirstByArticle_IdAndEntityKindAndDetectedNameIgnoreCaseAndStatusOrderByIdDesc(
                any(), any(), any(), any())).thenReturn(Optional.empty());

        EntityReviewDto.InternalResolveResponse response = service.resolveFranchise(
                request(57L, "Thief", "Thief 시리즈 경력"));

        assertThat(response.getOutcome()).isEqualTo(EntityReviewDto.ResolutionOutcome.AUTO_LINKED);
        assertThat(response.getFranchiseId()).isEqualTo(31L);
        verify(franchiseService).upsertIgdbCollection(2L, "Thief");
        verify(igdbClient, never()).searchGames(any(), anyInt());
    }

    @Test
    void adminFranchiseResolutionRemembersDetectedAliasAndPreservesDisplayNameWithoutIgdbCall() {
        NewsArticle article = org.mockito.Mockito.mock(NewsArticle.class);
        when(article.getId()).thenReturn(90L);

        EntityReview review = EntityReview.builder()
                .id(20L)
                .article(article)
                .entityKind(EntityReviewKind.FRANCHISE)
                .detectedName("Legacy Series")
                .aiEntityType("FRANCHISE")
                .primary(false)
                .confidenceScore(new BigDecimal("0.88"))
                .reason("series context")
                .status(EntityReviewStatus.PENDING)
                .build();

        Franchise franchise = Franchise.builder()
                .id(31L)
                .name("Canonical Series")
                .displayName("대표 시리즈명")
                .igdbCollectionId(2L)
                .build();

        when(entityReviewRepository.findById(20L)).thenReturn(Optional.of(review));
        when(franchiseRepository.findById(31L)).thenReturn(Optional.of(franchise));
        when(franchiseRepository.findExactIdentityCandidates("Legacy Series")).thenReturn(List.of());
        when(franchiseRepository.save(franchise)).thenReturn(franchise);
        when(articleFranchiseRepository.existsByArticle_IdAndFranchise_Id(90L, 31L)).thenReturn(false);
        when(entityReviewRepository.save(review)).thenReturn(review);
        when(topicIntegrationService.refreshRelationsForArticle(90L)).thenReturn(null);

        EntityReviewDto.AdminResponse response = service.resolveAdmin(
                20L,
                new EntityReviewDto.AdminResolveRequest(
                        EntityReviewDto.ResolutionType.FRANCHISE, 31L, null, null));

        assertThat(response.getStatus()).isEqualTo(EntityReviewStatus.RESOLVED);
        assertThat(franchise.getAliases()).extracting("alias").containsExactly("Legacy Series");
        assertThat(franchise.getDisplayName()).isEqualTo("대표 시리즈명");
        verify(franchiseRepository).save(franchise);
        verifyNoInteractions(igdbClient);
    }

    @Test
    void adminRecheckResolvedGameRefreshesExistingTopicAndKeepsArticleAnalysisUntouched() {
        NewsArticle article = org.mockito.Mockito.mock(NewsArticle.class);
        when(article.getId()).thenReturn(82L);
        when(article.getTitle()).thenReturn("Sony Accused of Blocking Marvel's Wolverine Discounts in Brazil");
        when(article.getSourceName()).thenReturn("Push Square");
        when(article.getUrl()).thenReturn("https://example.com/wolverine");

        EntityReview review = EntityReview.builder()
                .id(16L)
                .article(article)
                .entityKind(EntityReviewKind.GAME)
                .detectedName("Marvel’s Wolverine")
                .aiEntityType("SPECIFIC_GAME")
                .primary(true)
                .confidenceScore(new BigDecimal("0.99"))
                .reason("Marvel’s Wolverine 할인 기사")
                .status(EntityReviewStatus.PENDING)
                .build();

        Game mapped = org.mockito.Mockito.mock(Game.class);
        when(mapped.getId()).thenReturn(44L);
        when(mapped.getName()).thenReturn("Marvel's Wolverine");

        IgdbClient.IgdbGame canonical = game(168667L, "Marvel's Wolverine", "Main Game");

        when(entityReviewRepository.findById(16L)).thenReturn(Optional.of(review));
        when(newsArticleRepository.findById(82L)).thenReturn(Optional.of(article));
        when(gameIdentityService.findExactCandidates("Marvel’s Wolverine")).thenReturn(List.of());
        when(igdbClient.isConfigured()).thenReturn(true);
        when(igdbClient.findGamesByExactName("Marvel’s Wolverine", 20)).thenReturn(List.of(canonical));
        when(gameRepository.findByIgdbId(168667L)).thenReturn(Optional.of(mapped));
        when(articleGameRepository.existsByArticle_IdAndGame_Id(82L, 44L)).thenReturn(false);
        when(entityReviewRepository.findFirstByArticle_IdAndEntityKindAndDetectedNameIgnoreCaseAndStatusOrderByIdDesc(
                82L, EntityReviewKind.GAME, "Marvel’s Wolverine", EntityReviewStatus.PENDING))
                .thenReturn(Optional.of(review));
        when(topicIntegrationService.refreshRelationsForArticle(82L)).thenReturn(55L);

        EntityReviewDto.AdminResponse response = service.recheckAdmin(16L);

        assertThat(response.getStatus()).isEqualTo(EntityReviewStatus.RESOLVED);
        assertThat(response.getResolvedGameId()).isEqualTo(44L);
        verify(topicIntegrationService).refreshRelationsForArticle(82L);
        verify(eventPublisher).publishEvent(argThat((Object event) ->
                event instanceof EntityReviewResolvedEvent resolved && resolved.topicId().equals(55L)));
        verify(igdbClient, never()).findFranchisesByExactName(any(), anyInt());
        verify(igdbClient, never()).searchFranchises(any(), anyInt());
    }

    @Test
    void adminRecheckKeepsAmbiguousGamePendingWithoutTouchingTopic() throws Exception {
        NewsArticle article = org.mockito.Mockito.mock(NewsArticle.class);
        when(article.getId()).thenReturn(48L);
        when(article.getTitle()).thenReturn("Zero Hour coming to PS5, Xbox Series this fall");

        EntityReview review = EntityReview.builder()
                .id(8L)
                .article(article)
                .entityKind(EntityReviewKind.GAME)
                .detectedName("Zero Hour")
                .aiEntityType("SPECIFIC_GAME")
                .primary(true)
                .confidenceScore(new BigDecimal("0.98"))
                .reason("console release")
                .status(EntityReviewStatus.PENDING)
                .candidateJson("old")
                .build();

        when(entityReviewRepository.findById(8L)).thenReturn(Optional.of(review));
        when(newsArticleRepository.findById(48L)).thenReturn(Optional.of(article));
        when(gameIdentityService.findExactCandidates("Zero Hour")).thenReturn(List.of());
        when(igdbClient.isConfigured()).thenReturn(true);
        when(igdbClient.findGamesByExactName("Zero Hour", 20)).thenReturn(List.of(
                game(39344L, "Zero Hour", "Main Game"),
                game(146042L, "Zero Hour", "Main Game"),
                game(151730L, "Zero Hour", "Main Game")));
        when(entityReviewRepository.findFirstByArticle_IdAndEntityKindAndDetectedNameIgnoreCaseAndStatusOrderByIdDesc(
                48L, EntityReviewKind.GAME, "Zero Hour", EntityReviewStatus.PENDING))
                .thenReturn(Optional.of(review));
        when(objectMapper.writeValueAsString(any())).thenReturn("[]");
        when(entityReviewRepository.save(any(EntityReview.class))).then(returnsFirstArg());

        EntityReviewDto.AdminResponse response = service.recheckAdmin(8L);

        assertThat(response.getStatus()).isEqualTo(EntityReviewStatus.PENDING);
        verify(topicIntegrationService, never()).refreshRelationsForArticle(any());
        verify(eventPublisher, never()).publishEvent(any(EntityReviewResolvedEvent.class));
    }

    @Test
    void adminRecheckRefreshesPendingFranchiseWithIgdbSeriesCandidate() throws Exception {
        NewsArticle article = org.mockito.Mockito.mock(NewsArticle.class);
        when(article.getId()).thenReturn(57L);
        EntityReview review = EntityReview.builder()
                .id(12L)
                .article(article)
                .entityKind(EntityReviewKind.FRANCHISE)
                .detectedName("Thief")
                .aiEntityType("FRANCHISE")
                .primary(false)
                .confidenceScore(new BigDecimal("0.88"))
                .reason("Thief 시리즈 경력을 다룬 기사")
                .status(EntityReviewStatus.PENDING)
                .candidateJson("old")
                .build();

        IgdbClient.IgdbCollection thiefSeries = new IgdbClient.IgdbCollection();
        thiefSeries.setId(2L);
        thiefSeries.setName("Thief");

        when(entityReviewRepository.findById(12L)).thenReturn(Optional.of(review));
        when(newsArticleRepository.findById(57L)).thenReturn(Optional.of(article));
        when(franchiseRepository.findExactIdentityCandidates("Thief")).thenReturn(List.of());
        when(gameIdentityService.findExactCandidates("Thief")).thenReturn(List.of());
        when(igdbClient.isConfigured()).thenReturn(true);
        when(igdbClient.findFranchisesByExactName("Thief", 20)).thenReturn(List.of());
        when(igdbClient.findCollectionsByExactName("Thief", 20)).thenReturn(List.of(thiefSeries));
        when(igdbClient.searchFranchises("Thief", 5)).thenReturn(List.of());
        when(igdbClient.findGamesByExactName("Thief", 20)).thenReturn(List.of());
        when(igdbClient.searchGames("Thief", 5)).thenReturn(List.of());
        when(entityReviewRepository.findFirstByArticle_IdAndEntityKindAndDetectedNameIgnoreCaseAndStatusOrderByIdDesc(
                57L, EntityReviewKind.FRANCHISE, "Thief", EntityReviewStatus.PENDING))
                .thenReturn(Optional.of(review));
        when(objectMapper.writeValueAsString(any())).thenReturn("[]");
        when(entityReviewRepository.save(any(EntityReview.class))).then(returnsFirstArg());

        EntityReviewDto.AdminResponse response = service.recheckAdmin(12L);

        assertThat(response.getStatus()).isEqualTo(EntityReviewStatus.PENDING);
        verify(igdbClient).findCollectionsByExactName("Thief", 20);
        verify(objectMapper).writeValueAsString(argThat(value -> {
            if (!(value instanceof List<?> list)) return false;
            return list.stream().anyMatch(item -> item instanceof EntityReviewDto.Candidate candidate
                    && candidate.getEntityKind() == EntityReviewKind.FRANCHISE
                    && Long.valueOf(2L).equals(candidate.getIgdbCollectionId())
                    && "Thief".equals(candidate.getName()));
        }));
        verifyNoInteractions(topicIntegrationService);
    }

    @Test
    void compositeExpansionNameFallsBackToReviewCandidateWithoutAutoLink() throws Exception {
        NewsArticle article = org.mockito.Mockito.mock(NewsArticle.class);
        when(article.getId()).thenReturn(95L);
        when(newsArticleRepository.findById(95L)).thenReturn(Optional.of(article));

        String detected = "S.T.A.L.K.E.R. 2: Heart of Chornobyl – Cost of Hope";
        String collapsed = "S.T.A.L.K.E.R. 2: Cost of Hope";
        when(gameIdentityService.findExactCandidates(detected)).thenReturn(List.of());
        when(gameIdentityService.findExactCandidates(collapsed)).thenReturn(List.of());
        when(igdbClient.isConfigured()).thenReturn(true);
        when(igdbClient.findGamesByExactName(detected, 20)).thenReturn(List.of());
        when(igdbClient.searchGames(detected, 5)).thenReturn(List.of());
        when(igdbClient.findGamesByExactName(collapsed, 20))
                .thenReturn(List.of(game(396025L, collapsed, "Expansion")));
        when(objectMapper.writeValueAsString(any())).thenReturn("[]");
        when(entityReviewRepository.findFirstByArticle_IdAndEntityKindAndDetectedNameIgnoreCaseAndStatusOrderByIdDesc(
                any(), any(), any(), any())).thenReturn(Optional.empty());
        when(entityReviewRepository.save(any(EntityReview.class))).then(returnsFirstArg());

        EntityReviewDto.InternalResolveResponse response = service.resolveGame(
                request(95L, detected, "Cost of Hope review roundup"));

        assertThat(response.getOutcome()).isEqualTo(EntityReviewDto.ResolutionOutcome.REVIEW_REQUIRED);
        verify(igdbClient).findGamesByExactName(collapsed, 20);
        verify(igdbClient, never()).searchGames(collapsed, 5);
        verify(gameRepository, never()).findByIgdbId(396025L);
        verify(objectMapper).writeValueAsString(argThat(value -> {
            if (!(value instanceof List<?> list)) return false;
            return list.stream().anyMatch(item -> item instanceof EntityReviewDto.Candidate candidate
                    && candidate.getEntityKind() == EntityReviewKind.GAME
                    && Long.valueOf(396025L).equals(candidate.getIgdbId())
                    && collapsed.equals(candidate.getName()));
        }));
    }

    @Test
    void yearQualifiedGameFallsBackToCanonicalTitleForReviewAndKeepsReleaseYear() throws Exception {
        NewsArticle article = org.mockito.Mockito.mock(NewsArticle.class);
        when(article.getId()).thenReturn(174L);
        when(newsArticleRepository.findById(174L)).thenReturn(Optional.of(article));
        when(gameIdentityService.findExactCandidates("God of War (2018)")).thenReturn(List.of());
        when(gameIdentityService.findExactCandidates("God of War")).thenReturn(List.of());
        when(igdbClient.isConfigured()).thenReturn(true);
        when(igdbClient.findGamesByExactName("God of War (2018)", 20)).thenReturn(List.of());
        when(igdbClient.searchGames("God of War (2018)", 5)).thenReturn(List.of());

        IgdbClient.IgdbGame oldGame = game(100L, "God of War", "Main Game");
        oldGame.setFirstReleaseDate(1115856000L); // 2005-05-12 UTC
        IgdbClient.IgdbGame targetGame = game(19560L, "God of War", "Main Game");
        targetGame.setFirstReleaseDate(1524182400L); // 2018-04-20 UTC
        when(igdbClient.findGamesByExactName("God of War", 20)).thenReturn(List.of(oldGame, targetGame));
        when(objectMapper.writeValueAsString(any())).thenReturn("[]");
        when(entityReviewRepository.findFirstByArticle_IdAndEntityKindAndDetectedNameIgnoreCaseAndStatusOrderByIdDesc(
                any(), any(), any(), any())).thenReturn(Optional.empty());
        when(entityReviewRepository.save(any(EntityReview.class))).then(returnsFirstArg());

        EntityReviewDto.InternalResolveResponse response = service.resolveGame(
                request(174L, "God of War (2018)", "Amazon live-action series article"));

        assertThat(response.getOutcome()).isEqualTo(EntityReviewDto.ResolutionOutcome.REVIEW_REQUIRED);
        verify(igdbClient).findGamesByExactName("God of War", 20);
        verify(gameRepository, never()).findByIgdbId(19560L);
        verify(objectMapper).writeValueAsString(argThat(value -> {
            if (!(value instanceof List<?> list) || list.isEmpty()) return false;
            Object first = list.get(0);
            return first instanceof EntityReviewDto.Candidate candidate
                    && Long.valueOf(19560L).equals(candidate.getIgdbId())
                    && Integer.valueOf(2018).equals(candidate.getReleaseYear());
        }));
    }

    @Test
    void standardEditionFallsBackToBaseLookup() {
        NewsArticle article = org.mockito.Mockito.mock(NewsArticle.class);
        Game mapped = org.mockito.Mockito.mock(Game.class);
        when(article.getId()).thenReturn(104L);
        when(mapped.getId()).thenReturn(44L);
        when(mapped.getName()).thenReturn("Train Sim World 6");
        when(newsArticleRepository.findById(104L)).thenReturn(Optional.of(article));
        when(gameIdentityService.findExactCandidates("Train Sim World 6: Standard Edition")).thenReturn(List.of());
        when(gameIdentityService.findExactCandidates("Train Sim World 6")).thenReturn(List.of());
        when(igdbClient.isConfigured()).thenReturn(true);
        when(igdbClient.findGamesByExactName("Train Sim World 6: Standard Edition", 20)).thenReturn(List.of());
        when(igdbClient.searchGames("Train Sim World 6: Standard Edition", 5)).thenReturn(List.of());
        when(igdbClient.findGamesByExactName("Train Sim World 6", 20))
                .thenReturn(List.of(game(500L, "Train Sim World 6", "Main Game")));
        when(gameRepository.findByIgdbId(500L)).thenReturn(Optional.of(mapped));
        when(articleGameRepository.existsByArticle_IdAndGame_Id(104L, 44L)).thenReturn(false);
        when(entityReviewRepository.findFirstByArticle_IdAndEntityKindAndDetectedNameIgnoreCaseAndStatusOrderByIdDesc(
                any(), any(), any(), any())).thenReturn(Optional.empty());

        EntityReviewDto.InternalResolveResponse response = service.resolveGame(
                request(104L, "Train Sim World 6: Standard Edition", "Free Play Days"));

        assertThat(response.getOutcome()).isEqualTo(EntityReviewDto.ResolutionOutcome.AUTO_LINKED);
        assertThat(response.getGameId()).isEqualTo(44L);
        verify(igdbClient).findGamesByExactName("Train Sim World 6", 20);
        verify(mapped).addAlias("Train Sim World 6: Standard Edition");
    }

    private IgdbClient.IgdbGame game(Long id, String name, String type) {
        IgdbClient.IgdbGame raw = new IgdbClient.IgdbGame();
        raw.setId(id);
        raw.setName(name);
        IgdbClient.IgdbGameType gameType = new IgdbClient.IgdbGameType();
        gameType.setType(type);
        raw.setGameType(gameType);
        return raw;
    }

    private EntityReviewDto.InternalResolveRequest request(Long articleId, String name, String reason) {
        return EntityReviewDto.InternalResolveRequest.builder()
                .articleId(articleId)
                .detectedName(name)
                .entityType("SPECIFIC_GAME")
                .primary(true)
                .confidenceScore(new BigDecimal("0.99"))
                .reason(reason)
                .build();
    }
}
