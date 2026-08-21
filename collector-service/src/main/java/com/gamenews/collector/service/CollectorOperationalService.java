package com.gamenews.collector.service;

import com.gamenews.collector.dto.CollectorDto;
import com.gamenews.collector.source.RssSourceConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class CollectorOperationalService {

    private final RssSourceConfig sourceConfig;
    private final Map<String, SourceState> sourceStates = new ConcurrentHashMap<>();

    public void recordAttempt(String sourceKey) {
        sourceStates.compute(sourceKey, (key, current) -> {
            SourceState state = current != null ? current.copy() : new SourceState();
            state.lastAttemptAt = LocalDateTime.now();
            return state;
        });
    }

    public void recordSuccess(String sourceKey, CollectorDto.CollectionResult result) {
        sourceStates.compute(sourceKey, (key, current) -> {
            SourceState state = current != null ? current.copy() : new SourceState();
            LocalDateTime now = LocalDateTime.now();
            if (state.lastAttemptAt == null) {
                state.lastAttemptAt = now;
            }
            state.lastSuccessAt = now;
            state.fetched = result.getFetched();
            state.saved = result.getSaved();
            state.skipped = result.getSkipped();
            state.failed = result.getFailed();
            state.lastError = null;
            return state;
        });
    }

    public void recordFailure(String sourceKey, Exception error) {
        sourceStates.compute(sourceKey, (key, current) -> {
            SourceState state = current != null ? current.copy() : new SourceState();
            if (state.lastAttemptAt == null) {
                state.lastAttemptAt = LocalDateTime.now();
            }
            state.fetched = 0;
            state.saved = 0;
            state.skipped = 0;
            state.failed = 1;
            state.lastError = safeMessage(error);
            return state;
        });
    }

    public CollectorDto.OperationalStatus getStatus() {
        List<CollectorDto.SourceOperationalStatus> sources = sourceConfig.getSources().entrySet().stream()
                .map(entry -> toStatus(entry.getKey(), entry.getValue()))
                .toList();

        return CollectorDto.OperationalStatus.builder()
                .status("UP")
                .scheduleEnabled(sourceConfig.getSchedule().isEnabled())
                .generatedAt(LocalDateTime.now())
                .sources(sources)
                .build();
    }

    private CollectorDto.SourceOperationalStatus toStatus(
            String sourceKey,
            RssSourceConfig.Source source) {
        SourceState state = sourceStates.get(sourceKey);
        return CollectorDto.SourceOperationalStatus.builder()
                .key(sourceKey)
                .name(source.getName())
                .enabled(source.isEnabled())
                .lastAttemptAt(state != null ? state.lastAttemptAt : null)
                .lastSuccessAt(state != null ? state.lastSuccessAt : null)
                .fetched(state != null ? state.fetched : 0)
                .saved(state != null ? state.saved : 0)
                .skipped(state != null ? state.skipped : 0)
                .failed(state != null ? state.failed : 0)
                .lastError(state != null ? state.lastError : null)
                .build();
    }

    private String safeMessage(Exception error) {
        if (error == null) {
            return "unknown error";
        }
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            return error.getClass().getSimpleName();
        }
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }

    private static final class SourceState {
        private LocalDateTime lastAttemptAt;
        private LocalDateTime lastSuccessAt;
        private int fetched;
        private int saved;
        private int skipped;
        private int failed;
        private String lastError;

        private SourceState copy() {
            SourceState copy = new SourceState();
            copy.lastAttemptAt = this.lastAttemptAt;
            copy.lastSuccessAt = this.lastSuccessAt;
            copy.fetched = this.fetched;
            copy.saved = this.saved;
            copy.skipped = this.skipped;
            copy.failed = this.failed;
            copy.lastError = this.lastError;
            return copy;
        }
    }
}
