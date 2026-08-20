package com.gamenews.news.service;

import com.gamenews.news.entity.Game;
import com.gamenews.news.entity.GameAlias;
import com.gamenews.news.repository.GameAliasRepository;
import com.gamenews.news.repository.GameRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GameIdentityService {

    private final GameRepository gameRepository;
    private final GameAliasRepository gameAliasRepository;

    public Optional<Game> findExact(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return Optional.empty();
        }

        Optional<Game> byName = gameRepository.findByNameIgnoreCase(normalized);
        if (byName.isPresent()) {
            return byName;
        }

        Optional<Game> byDisplayName = gameRepository.findByDisplayNameIgnoreCase(normalized);
        if (byDisplayName.isPresent()) {
            return byDisplayName;
        }

        return gameAliasRepository.findByAliasIgnoreCase(normalized)
                .map(GameAlias::getGame);
    }

    public String normalizeDisplayName(String name, String displayName) {
        String normalized = trimToNull(displayName);
        if (normalized != null && name != null && normalized.equalsIgnoreCase(name.trim())) {
            return null;
        }
        return normalized;
    }

    public List<String> normalizeAliases(String name, String displayName, List<String> aliases) {
        if (aliases == null) {
            return null;
        }

        Map<String, String> unique = new LinkedHashMap<>();
        for (String alias : aliases) {
            String normalized = trimToNull(alias);
            if (normalized == null) {
                continue;
            }
            if (name != null && normalized.equalsIgnoreCase(name.trim())) {
                continue;
            }
            if (displayName != null && normalized.equalsIgnoreCase(displayName.trim())) {
                continue;
            }
            unique.putIfAbsent(normalized.toLowerCase(Locale.ROOT), normalized);
        }
        return new ArrayList<>(unique.values());
    }

    public void validateAvailable(
            Long currentGameId,
            String name,
            String displayName,
            List<String> aliases) {
        List<String> identities = new ArrayList<>();
        addIfPresent(identities, name);
        addIfPresent(identities, displayName);
        if (aliases != null) {
            aliases.forEach(alias -> addIfPresent(identities, alias));
        }

        for (String identity : identities) {
            findExact(identity)
                    .filter(existing -> currentGameId == null || !existing.getId().equals(currentGameId))
                    .ifPresent(existing -> {
                        throw new IllegalArgumentException(
                                "이미 다른 게임에서 사용 중인 이름 또는 별칭입니다: " + identity);
                    });
        }
    }

    private void addIfPresent(List<String> values, String value) {
        String normalized = trimToNull(value);
        if (normalized != null) {
            values.add(normalized);
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
