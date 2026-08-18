package com.gamenews.collector.source;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "collector")
public class RssSourceConfig {

    private Schedule schedule = new Schedule();
    private StartupCatchup startupCatchup = new StartupCatchup();
    private Map<String, Source> sources = new LinkedHashMap<>();

    public Source getRequiredSource(String sourceKey) {
        Source source = sources.get(sourceKey);
        if (source == null) {
            throw new IllegalArgumentException("지원하지 않는 수집 출처입니다: " + sourceKey);
        }
        return source;
    }

    @Getter
    @Setter
    public static class Schedule {
        private boolean enabled = true;
        private long initialDelay = 10_000L;
        private long fixedDelay = 600_000L;
    }

    @Getter
    @Setter
    public static class StartupCatchup {
        private boolean enabled = true;
        private int limit = 50;
    }

    @Getter
    @Setter
    public static class Source {
        private String name;
        private String sourceType = "MEDIA";
        private String rssUrl;
        private boolean enabled = false;
        private int limit = 10;
    }
}
