package com.gamenews.collector;

import com.gamenews.collector.dto.CollectorDto;
import com.gamenews.collector.service.CollectorOperationalService;
import com.gamenews.collector.source.RssSourceConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CollectorOperationalServiceTest {

    private CollectorOperationalService service;

    @BeforeEach
    void setUp() {
        RssSourceConfig config = new RssSourceConfig();
        config.getSchedule().setEnabled(true);

        RssSourceConfig.Source source = new RssSourceConfig.Source();
        source.setName("PC Gamer");
        source.setEnabled(true);
        source.setLimit(10);
        source.setRssUrl("https://example.com/rss");

        Map<String, RssSourceConfig.Source> sources = new LinkedHashMap<>();
        sources.put("pcgamer", source);
        config.setSources(sources);

        service = new CollectorOperationalService(config);
    }

    @Test
    void exposesLastSuccessfulCollectionSnapshot() {
        service.recordAttempt("pcgamer");
        service.recordSuccess("pcgamer", CollectorDto.CollectionResult.builder()
                .source("PC Gamer")
                .fetched(10)
                .saved(2)
                .skipped(8)
                .failed(0)
                .build());

        CollectorDto.OperationalStatus status = service.getStatus();
        CollectorDto.SourceOperationalStatus source = status.getSources().getFirst();

        assertThat(status.getStatus()).isEqualTo("UP");
        assertThat(status.isScheduleEnabled()).isTrue();
        assertThat(source.getKey()).isEqualTo("pcgamer");
        assertThat(source.getLastAttemptAt()).isNotNull();
        assertThat(source.getLastSuccessAt()).isNotNull();
        assertThat(source.getFetched()).isEqualTo(10);
        assertThat(source.getSaved()).isEqualTo(2);
        assertThat(source.getSkipped()).isEqualTo(8);
        assertThat(source.getFailed()).isZero();
        assertThat(source.getLastError()).isNull();
    }

    @Test
    void exposesLatestSourceFailureWithoutLosingLastSuccessTimestamp() {
        service.recordAttempt("pcgamer");
        service.recordSuccess("pcgamer", CollectorDto.CollectionResult.builder()
                .source("PC Gamer")
                .fetched(10)
                .saved(1)
                .skipped(9)
                .failed(0)
                .build());
        var successAt = service.getStatus().getSources().getFirst().getLastSuccessAt();

        service.recordAttempt("pcgamer");
        service.recordFailure("pcgamer", new IllegalStateException("response timeout after 15s"));

        CollectorDto.SourceOperationalStatus source = service.getStatus().getSources().getFirst();
        assertThat(source.getLastSuccessAt()).isEqualTo(successAt);
        assertThat(source.getFetched()).isZero();
        assertThat(source.getSaved()).isZero();
        assertThat(source.getSkipped()).isZero();
        assertThat(source.getFailed()).isEqualTo(1);
        assertThat(source.getLastError()).contains("response timeout");
    }
}
