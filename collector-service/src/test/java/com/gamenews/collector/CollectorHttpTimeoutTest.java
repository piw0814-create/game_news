package com.gamenews.collector;

import com.gamenews.collector.client.NewsServiceClient;
import com.gamenews.collector.config.WebClientConfig;
import com.gamenews.collector.dto.CollectorDto;
import com.gamenews.collector.exception.NewsServiceUnavailableException;
import com.gamenews.collector.source.GenericRssClient;
import com.gamenews.collector.source.RssSourceConfig;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CollectorHttpTimeoutTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void rssResponseTimeoutFailsTheSourceRequest() throws Exception {
        server = delayedServer("/feed", 1_500, rssBody(), "application/rss+xml", 200);

        RssSourceConfig config = timeoutConfig();
        WebClient.Builder builder = new WebClientConfig().webClientBuilder(config);
        GenericRssClient client = new GenericRssClient(builder, config);

        RssSourceConfig.Source source = new RssSourceConfig.Source();
        source.setName("Slow RSS");
        source.setRssUrl(baseUrl() + "/feed");

        assertThatThrownBy(() -> client.fetchLatest(source, 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Slow RSS RSS 수집에 실패했습니다");
    }

    @Test
    void newsService5xxIsClassifiedAsInfrastructureFailure() throws Exception {
        server = delayedServer("/api/news", 0, "temporary failure", "text/plain", 503);

        RssSourceConfig config = timeoutConfig();
        WebClient.Builder builder = new WebClientConfig().webClientBuilder(config);
        NewsServiceClient client = new NewsServiceClient(builder, config);
        ReflectionTestUtils.setField(client, "newsServiceBaseUrl", baseUrl());

        CollectorDto.NewsCreateRequest request = CollectorDto.NewsCreateRequest.builder()
                .title("title")
                .url("https://example.com/article")
                .sourceName("Test")
                .sourceType("MEDIA")
                .build();

        assertThatThrownBy(() -> client.createNews(request))
                .isInstanceOf(NewsServiceUnavailableException.class)
                .hasMessageContaining("HTTP 503");
    }

    @Test
    void newsServiceResponseTimeoutIsClassifiedAsInfrastructureFailure() throws Exception {
        server = delayedServer("/api/news", 1_500, "{}", "application/json", 201);

        RssSourceConfig config = timeoutConfig();
        WebClient.Builder builder = new WebClientConfig().webClientBuilder(config);
        NewsServiceClient client = new NewsServiceClient(builder, config);
        ReflectionTestUtils.setField(client, "newsServiceBaseUrl", baseUrl());

        CollectorDto.NewsCreateRequest request = CollectorDto.NewsCreateRequest.builder()
                .title("title")
                .url("https://example.com/article")
                .sourceName("Test")
                .sourceType("MEDIA")
                .build();

        assertThatThrownBy(() -> client.createNews(request))
                .isInstanceOf(NewsServiceUnavailableException.class)
                .hasMessageContaining("timeout");
    }

    private RssSourceConfig timeoutConfig() {
        RssSourceConfig config = new RssSourceConfig();
        config.getHttp().setConnectTimeoutMs(500);
        config.getHttp().setRssTimeoutSeconds(1);
        config.getHttp().setNewsTimeoutSeconds(1);
        return config;
    }

    private HttpServer delayedServer(
            String path,
            long delayMillis,
            String body,
            String contentType,
            int status
    ) throws IOException {
        HttpServer httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        httpServer.createContext(path, exchange -> {
            try {
                Thread.sleep(delayMillis);
                byte[] response = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", contentType);
                exchange.sendResponseHeaders(status, response.length);
                exchange.getResponseBody().write(response);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (IOException ignored) {
                // The client may close the socket after its timeout, which is expected here.
            } finally {
                exchange.close();
            }
        });
        httpServer.start();
        return httpServer;
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private String rssBody() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0">
                  <channel>
                    <title>Test</title>
                    <link>https://example.com</link>
                    <description>Test Feed</description>
                    <item>
                      <title>Article</title>
                      <link>https://example.com/article</link>
                      <description>Body</description>
                    </item>
                  </channel>
                </rss>
                """;
    }
}
