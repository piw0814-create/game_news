package com.gamenews.collector.scheduler;

import com.gamenews.collector.dto.CollectorDto;
import com.gamenews.collector.service.CollectorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "collector.sources.destructoid",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class DestructoidCollectorScheduler {

    private final CollectorService collectorService;

    @Value("${collector.sources.destructoid.limit:10}")
    private int limit;

    @Scheduled(
            initialDelayString = "${collector.sources.destructoid.initial-delay:20000}",
            fixedDelayString = "${collector.sources.destructoid.fixed-delay:600000}"
    )
    public void collectDestructoid() {
        log.info("[DestructoidCollectorScheduler] Destructoid 자동 수집 시작 - limit={}", limit);

        try {
            CollectorDto.CollectionResult result = collectorService.collectDestructoid(limit);
            log.info(
                    "[DestructoidCollectorScheduler] Destructoid 자동 수집 완료 - fetched={}, saved={}, skipped={}, failed={}",
                    result.getFetched(),
                    result.getSaved(),
                    result.getSkipped(),
                    result.getFailed()
            );
        } catch (Exception e) {
            log.error("[DestructoidCollectorScheduler] Destructoid 자동 수집 실패 - error={}", e.getMessage(), e);
        }
    }
}
