package com.gamenews.collector.scheduler;

import com.gamenews.collector.dto.CollectorDto;
import com.gamenews.collector.service.CollectorOperationalService;
import com.gamenews.collector.service.CollectorService;
import com.gamenews.collector.source.RssSourceConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class RssCollectorScheduler {

    private final CollectorService collectorService;
    private final RssSourceConfig sourceConfig;
    private final CollectorOperationalService operationalService;
    private final Set<String> startupCatchupCompletedSources = ConcurrentHashMap.newKeySet();

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

            boolean startupCatchup = shouldRunStartupCatchup(sourceKey);
            int limit = startupCatchup
                    ? sourceConfig.getStartupCatchup().getLimit()
                    : source.getLimit();

            if (startupCatchup) {
                log.info("[RssCollectorScheduler] startup catch-up 시작 - key={}, source={}, limit={}",
                        sourceKey, source.getName(), limit);
            } else {
                log.info("[RssCollectorScheduler] 자동 수집 시작 - key={}, source={}, limit={}",
                        sourceKey, source.getName(), limit);
            }

            operationalService.recordAttempt(sourceKey);

            try {
                CollectorDto.CollectionResult result = startupCatchup
                        ? collectorService.collectStartupCatchup(sourceKey, limit)
                        : collectorService.collectScheduled(sourceKey, limit);

                operationalService.recordSuccess(sourceKey, result);

                if (startupCatchup) {
                    handleStartupCatchupResult(sourceKey, result);
                } else {
                    log.info(
                            "[RssCollectorScheduler] 자동 수집 완료 - source={}, fetched={}, saved={}, skipped={}, failed={}",
                            result.getSource(),
                            result.getFetched(),
                            result.getSaved(),
                            result.getSkipped(),
                            result.getFailed()
                    );
                }
            } catch (Exception e) {
                operationalService.recordFailure(sourceKey, e);
                if (startupCatchup) {
                    log.error("[RssCollectorScheduler] startup catch-up 실패 - key={}, source={}, error={}",
                            sourceKey, source.getName(), e.getMessage(), e);
                } else {
                    log.error("[RssCollectorScheduler] 자동 수집 실패 - key={}, source={}, error={}",
                            sourceKey, source.getName(), e.getMessage(), e);
                }
            }
        }
    }

    private boolean shouldRunStartupCatchup(String sourceKey) {
        return sourceConfig.getStartupCatchup().isEnabled()
                && !startupCatchupCompletedSources.contains(sourceKey);
    }

    private void handleStartupCatchupResult(
            String sourceKey,
            CollectorDto.CollectionResult result) {

        if (isStartupCatchupSuccessful(result)) {
            startupCatchupCompletedSources.add(sourceKey);
            log.info(
                    "[RssCollectorScheduler] startup catch-up 완료 - source={}, fetched={}, saved={}, skipped={}, failed={}",
                    result.getSource(),
                    result.getFetched(),
                    result.getSaved(),
                    result.getSkipped(),
                    result.getFailed()
            );
            return;
        }

        log.warn(
                "[RssCollectorScheduler] startup catch-up 미완료 - source={}, fetched={}, saved={}, skipped={}, failed={} - 다음 자동 수집에서 재시도",
                result.getSource(),
                result.getFetched(),
                result.getSaved(),
                result.getSkipped(),
                result.getFailed()
        );
    }

    private boolean isStartupCatchupSuccessful(CollectorDto.CollectionResult result) {
        if (result.getFetched() == 0) {
            return true;
        }
        return result.getFailed() < result.getFetched();
    }
}
