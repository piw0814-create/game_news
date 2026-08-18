package com.gamenews.collector.source;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;

@Component
@RequiredArgsConstructor
public class PcGamerRssClient {

    private static final String SOURCE_NAME = "PC Gamer";
    private static final String USER_AGENT = "game-news-collector/1.0";

    private final WebClient.Builder webClientBuilder;

    @Value("${collector.sources.pcgamer.rss-url}")
    private String rssUrl;

    public List<RssArticle> fetchLatest(int limit) {
        try {
            byte[] rssBytes = webClientBuilder.build()
                    .get()
                    .uri(rssUrl)
                    .header(HttpHeaders.USER_AGENT, USER_AGENT)
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .block();

            if (rssBytes == null || rssBytes.length == 0) {
                throw new IllegalStateException("PC Gamer RSS 응답이 비어 있습니다");
            }

            try (XmlReader reader = new XmlReader(new ByteArrayInputStream(rssBytes))) {
                SyndFeed feed = new SyndFeedInput().build(reader);

                return feed.getEntries().stream()
                        .limit(limit)
                        .map(this::toArticle)
                        .toList();
            }
        } catch (Exception e) {
            throw new IllegalStateException("PC Gamer RSS 수집에 실패했습니다: " + e.getMessage(), e);
        }
    }

    private RssArticle toArticle(SyndEntry entry) {
        String content = entry.getDescription() == null
                ? null
                : entry.getDescription().getValue();

        Date publishedDate = entry.getPublishedDate();

        LocalDateTime publishedAt = publishedDate == null
                ? null
                : LocalDateTime.ofInstant(publishedDate.toInstant(), ZoneOffset.UTC);

        return RssArticle.builder()
                .title(entry.getTitle())
                .url(entry.getLink())
                .publishedAt(publishedAt)
                .content(content)
                .build();
    }

    public String getSourceName() {
        return SOURCE_NAME;
    }
}
