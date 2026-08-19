package com.gamenews.interest.service;

import com.gamenews.interest.dto.InterestDto;
import com.gamenews.interest.entity.UserGame;
import com.gamenews.interest.repository.UserGameRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InterestService {

    private final UserGameRepository userGameRepository;
    private final GameServiceClient gameServiceClient;

    @Transactional
    public InterestDto.InterestResponse addGame(Long userId, Long gameId) {
        InterestDto.GameSummary game = gameServiceClient.getGame(gameId);

        if (userGameRepository.existsByUserIdAndGameId(userId, gameId)) {
            throw new IllegalArgumentException("이미 관심 게임으로 등록되어 있습니다: " + gameId);
        }

        try {
            UserGame userGame = userGameRepository.save(
                    UserGame.builder()
                            .userId(userId)
                            .gameId(gameId)
                            .build()
            );

            log.info("[InterestService] 관심 게임 등록 - userId={}, gameId={}", userId, gameId);
            return InterestDto.InterestResponse.from(userGame, game);
        } catch (DataIntegrityViolationException e) {
            throw new IllegalArgumentException("이미 관심 게임으로 등록되어 있습니다: " + gameId);
        }
    }

    public List<InterestDto.InterestResponse> getMyGames(Long userId) {
        return userGameRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(userGame -> InterestDto.InterestResponse.from(
                        userGame,
                        gameServiceClient.getGame(userGame.getGameId())
                ))
                .toList();
    }

    public List<Long> getMyGameIds(Long userId) {
        return userGameRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(UserGame::getGameId)
                .toList();
    }

    @Transactional
    public void removeGame(Long userId, Long gameId) {
        UserGame userGame = userGameRepository.findByUserIdAndGameId(userId, gameId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "관심 게임을 찾을 수 없습니다: " + gameId));

        userGameRepository.delete(userGame);
        log.info("[InterestService] 관심 게임 해제 - userId={}, gameId={}", userId, gameId);
    }

    @Transactional
    public void mergeGameReferences(Long sourceGameId, Long targetGameId) {
        if (sourceGameId.equals(targetGameId)) {
            return;
        }

        List<UserGame> sourceInterests = userGameRepository.findAllByGameIdOrderByIdAsc(sourceGameId);
        int moved = 0;
        int duplicatesRemoved = 0;

        for (UserGame sourceInterest : sourceInterests) {
            if (userGameRepository.existsByUserIdAndGameId(sourceInterest.getUserId(), targetGameId)) {
                userGameRepository.delete(sourceInterest);
                duplicatesRemoved++;
            } else {
                sourceInterest.changeGameId(targetGameId);
                moved++;
            }
        }

        log.info(
                "[InterestService] Game 관심관계 병합 - sourceGameId={}, targetGameId={}, moved={}, duplicatesRemoved={}",
                sourceGameId, targetGameId, moved, duplicatesRemoved);
    }

    @Transactional
    public void deleteGameReferences(Long gameId) {
        List<UserGame> interests = userGameRepository.findAllByGameIdOrderByIdAsc(gameId);
        userGameRepository.deleteAll(interests);
        log.info("[InterestService] Game 관심관계 제거 - gameId={}, deleted={}", gameId, interests.size());
    }
}
