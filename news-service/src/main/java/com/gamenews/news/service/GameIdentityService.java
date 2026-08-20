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

    /**
     * 이름/표시명/별칭이 정확히 하나의 Game만 가리킬 때만 반환한다.
     * IGDB는 서로 다른 Game에 같은 name을 허용하므로 복수 후보는 자동 결정하지 않는다.
     */
    public Optional<Game> findExact(String value) {
        List<Game> candidates = findExactCandidates(value);
        return candidates.size() == 1 ? Optional.of(candidates.get(0)) : Optional.empty();
    }

    public List<Game> findExactCandidates(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return List.of();
        }

        Map<Long, Game> unique = new LinkedHashMap<>();
        gameRepository.findAllByNameIgnoreCase(normalized)
                .forEach(game -> unique.putIfAbsent(game.getId(), game));
        gameRepository.findAllByDisplayNameIgnoreCase(normalized)
                .forEach(game -> unique.putIfAbsent(game.getId(), game));
        gameAliasRepository.findByAliasIgnoreCase(normalized)
                .map(GameAlias::getGame)
                .ifPresent(game -> unique.putIfAbsent(game.getId(), game));
        return new ArrayList<>(unique.values());
    }

    public boolean isIdentityUsedByAnotherGame(Long currentGameId, String value) {
        return findExactCandidates(value).stream()
                .anyMatch(existing -> currentGameId == null || !existing.getId().equals(currentGameId));
    }

    public void validateIdentityAvailable(Long currentGameId, String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return;
        }
        if (isIdentityUsedByAnotherGame(currentGameId, normalized)) {
            throw new IllegalArgumentException(
                    "이미 다른 게임에서 사용 중인 이름 또는 별칭입니다: " + normalized);
        }
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

    /**
     * 수동 생성/수정에서 새로 사용하는 식별자는 계속 유일하게 관리한다.
     * 동일 canonical name을 가진 서로 다른 IGDB Game만 카탈로그 레벨에서 허용한다.
     */
    public void validateAvailable(
            Long currentGameId,
            String name,
            String displayName,
            List<String> aliases) {
        validateIdentityAvailable(currentGameId, name);
        validateIdentityAvailable(currentGameId, displayName);
        if (aliases != null) {
            aliases.forEach(alias -> validateIdentityAvailable(currentGameId, alias));
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
