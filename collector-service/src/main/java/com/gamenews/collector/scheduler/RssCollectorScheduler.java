package com.gamenews.collector.scheduler;

import com.gamenews.collector.dto.CollectorDto;
import com.gamenews.collector.service.CollectorService;
import com.gamenews.collector.source.RssSourceConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class RssCollectorScheduler {

    private final CollectorService collectorService;
    private final RssSourceConfig sourceConfig;

    @Scheduled(
            initialDelayString = "${collector.schedule.initial-delay:10000}",
            fixedDelayString = "${collector.schedule.fixed-delay:600000}"
    )
    public void collectEnabledSources() {
        if (!sourceConfig.getSchedule().isEnabled()) {
            return;
        }

        for (Map.Entry<String, RssSourceConfig.Source> entry : sourceConfig.getSources().entrySet()) {
            String sourceKey = entry.getKey();
            RssSourceConfig.Source source = entry.getValue();

            if (!source.isEnabled()) {
                continue;
            }

            log.info("[RssCollectorScheduler] 자동 수집 시작 - key={}, source={}, limit={}",
                    sourceKey, source.getName(), source.getLimit());

            try {
                CollectorDto.CollectionResult result = collectorService.collectScheduled(sourceKey);
                log.info(
                        "[RssCollectorScheduler] 자동 수집 완료 - source={}, fetched={}, saved={}, skipped={}, failed={}",
                        result.getSource(),
                        result.getFetched(),
                        result.getSaved(),
                        result.getSkipped(),
                        result.getFailed()
                );
            } catch (Exception e) {
                log.error("[RssCollectorScheduler] 자동 수집 실패 - key={}, source={}, error={}",
                        sourceKey, source.getName(), e.getMessage(), e);
            }
        }
    }
}
