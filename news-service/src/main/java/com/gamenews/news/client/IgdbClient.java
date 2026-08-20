package com.gamenews.news.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.gamenews.news.config.IgdbProperties;
import com.gamenews.news.exception.IgdbIntegrationException;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import io.netty.channel.ChannelOption;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Component
@Slf4j
public class IgdbClient {

    private static final String GAME_FIELDS = String.join(",",
            "id",
            "name",
            "alternative_names.name",
            "alternative_names.comment",
            "game_localizations.name",
            "game_localizations.region.identifier",
            "game_localizations.region.name",
            "genres.name",
            "platforms.name",
            "cover.url",
            "involved_companies.company.name",
            "involved_companies.developer",
            "involved_companies.publisher",
            "franchise.id",
            "franchise.name",
            "franchises.id",
            "franchises.name");

    private static final int CONNECT_TIMEOUT_MILLIS = 5_000;
    private static final Duration RESPONSE_TIMEOUT = Duration.ofSeconds(8);

    private final IgdbProperties properties;
    private final WebClient webClient;

    public IgdbClient(WebClient.Builder webClientBuilder, IgdbProperties properties) {
        this.properties = properties;

        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIMEOUT_MILLIS)
                .responseTimeout(RESPONSE_TIMEOUT);

        this.webClient = webClientBuilder.clone()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    private volatile String accessToken;
    private volatile Instant accessTokenExpiresAt = Instant.EPOCH;

    public boolean isConfigured() {
        return properties.isConfigured();
    }

    public List<IgdbGame> searchGames(String searchTerm, int limit) {
        ensureConfigured();
        String safeSearch = escapeApicalypseString(searchTerm);
        int safeLimit = Math.max(1, Math.min(limit, 10));
        String body = "fields " + GAME_FIELDS + "; "
                + "search \"" + safeSearch + "\"; "
                + "where version_parent = null; "
                + "limit " + safeLimit + ";";
        return requestGames(body, "search query=\"" + safeLogValue(searchTerm) + "\"");
    }

    public IgdbGame getGameById(Long igdbId) {
        ensureConfigured();
        String body = "fields " + GAME_FIELDS + "; where id = " + igdbId + "; limit 1;";
        return requestGames(body, "game id=" + igdbId).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("IGDB 게임을 찾을 수 없습니다: " + igdbId));
    }

    private List<IgdbGame> requestGames(String body, String operation) {
        long startedAt = System.nanoTime();
        try {
            List<IgdbGame> result = webClient
                    .post()
                    .uri(properties.getApiBaseUrl() + "/games")
                    .header("Client-ID", properties.getClientId())
                    .header("Authorization", "Bearer " + getAccessToken())
                    .contentType(MediaType.TEXT_PLAIN)
                    .accept(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<IgdbGame>>() {})
                    .block();
            List<IgdbGame> safeResult = result == null ? List.of() : result;
            log.info("[IGDB] Request completed - {}, count={}, {}ms",
                    operation, safeResult.size(), elapsedMillis(startedAt));
            return safeResult;
        } catch (WebClientResponseException e) {
            log.warn("[IGDB] Request failed - {}, HTTP {}, {}ms",
                    operation, e.getStatusCode().value(), elapsedMillis(startedAt));
            throw new IgdbIntegrationException(
                    "IGDB 요청에 실패했습니다. HTTP " + e.getStatusCode().value(), e);
        } catch (WebClientRequestException e) {
            long elapsed = elapsedMillis(startedAt);
            log.warn("[IGDB] Network request failed - {}, {}ms, cause={}",
                    operation, elapsed, rootCauseName(e));
            if (isTimeout(e)) {
                throw new IgdbIntegrationException("IGDB 요청 시간이 초과되었습니다", e);
            }
            throw new IgdbIntegrationException("IGDB 네트워크 요청 중 오류가 발생했습니다", e);
        } catch (IgdbIntegrationException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[IGDB] Unexpected request error - {}, {}ms, cause={}",
                    operation, elapsedMillis(startedAt), rootCauseName(e));
            throw new IgdbIntegrationException("IGDB 요청 중 오류가 발생했습니다", e);
        }
    }

    private String getAccessToken() {
        Instant now = Instant.now();
        if (accessToken != null && now.isBefore(accessTokenExpiresAt.minusSeconds(60))) {
            return accessToken;
        }
        return refreshAccessToken();
    }

    private synchronized String refreshAccessToken() {
        Instant now = Instant.now();
        if (accessToken != null && now.isBefore(accessTokenExpiresAt.minusSeconds(60))) {
            return accessToken;
        }

        long startedAt = System.nanoTime();
        try {
            TokenResponse token = webClient
                    .post()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https")
                            .host("id.twitch.tv")
                            .path("/oauth2/token")
                            .queryParam("client_id", properties.getClientId())
                            .queryParam("client_secret", properties.getClientSecret())
                            .queryParam("grant_type", "client_credentials")
                            .build())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(TokenResponse.class)
                    .block();

            if (token == null || token.getAccessToken() == null || token.getAccessToken().isBlank()) {
                throw new IgdbIntegrationException("IGDB 인증 토큰을 발급받지 못했습니다");
            }

            this.accessToken = token.getAccessToken();
            this.accessTokenExpiresAt = now.plusSeconds(Math.max(120, token.getExpiresIn()));
            log.info("[IGDB] OAuth token acquired - {}ms", elapsedMillis(startedAt));
            return accessToken;
        } catch (WebClientResponseException e) {
            log.warn("[IGDB] OAuth token request failed - HTTP {}, {}ms",
                    e.getStatusCode().value(), elapsedMillis(startedAt));
            throw new IgdbIntegrationException(
                    "IGDB 인증에 실패했습니다. Client ID/Secret을 확인하세요. HTTP "
                            + e.getStatusCode().value(), e);
        } catch (WebClientRequestException e) {
            log.warn("[IGDB] OAuth token network request failed - {}ms, cause={}",
                    elapsedMillis(startedAt), rootCauseName(e));
            if (isTimeout(e)) {
                throw new IgdbIntegrationException("IGDB 인증 요청 시간이 초과되었습니다", e);
            }
            throw new IgdbIntegrationException("IGDB 인증 서버 연결 중 오류가 발생했습니다", e);
        }
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private boolean isTimeout(Throwable error) {
        Throwable current = error;
        while (current != null) {
            String type = current.getClass().getSimpleName().toLowerCase(Locale.ROOT);
            String message = current.getMessage() == null
                    ? ""
                    : current.getMessage().toLowerCase(Locale.ROOT);
            if (type.contains("timeout") || message.contains("timed out") || message.contains("timeout")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private String rootCauseName(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getClass().getSimpleName();
    }

    private String safeLogValue(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.replace('\r', ' ').replace('\n', ' ').trim();
        return normalized.length() <= 80 ? normalized : normalized.substring(0, 80) + "...";
    }

    private void ensureConfigured() {
        if (!properties.isConfigured()) {
            throw new IllegalArgumentException(
                    "IGDB 설정이 없습니다. IGDB_CLIENT_ID와 IGDB_CLIENT_SECRET을 설정하세요");
        }
    }

    private String escapeApicalypseString(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", " ")
                .replace("\n", " ")
                .trim();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TokenResponse {
        @JsonProperty("access_token")
        private String accessToken;

        @JsonProperty("expires_in")
        private long expiresIn;

        @JsonProperty("token_type")
        private String tokenType;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class IgdbGame {
        private Long id;
        private String name;
        @JsonProperty("alternative_names")
        private List<IgdbAlternativeName> alternativeNames;
        @JsonProperty("game_localizations")
        private List<IgdbLocalization> gameLocalizations;
        private List<IgdbNamedEntity> genres;
        private List<IgdbNamedEntity> platforms;
        private IgdbCover cover;
        @JsonProperty("involved_companies")
        private List<IgdbInvolvedCompany> involvedCompanies;
        private IgdbNamedEntity franchise;
        private List<IgdbNamedEntity> franchises;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class IgdbAlternativeName {
        private String name;
        private String comment;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class IgdbLocalization {
        private String name;
        private IgdbRegion region;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class IgdbRegion {
        private String identifier;
        private String name;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class IgdbNamedEntity {
        private Long id;
        private String name;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class IgdbCover {
        private String url;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class IgdbInvolvedCompany {
        private IgdbNamedEntity company;
        private boolean developer;
        private boolean publisher;
    }
}
