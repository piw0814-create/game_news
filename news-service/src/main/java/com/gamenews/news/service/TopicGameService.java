package com.gamenews.news.service;

import com.gamenews.news.dto.TopicGameDto;
import com.gamenews.news.entity.Game;
import com.gamenews.news.entity.Topic;
import com.gamenews.news.entity.TopicGame;
import com.gamenews.news.repository.GameRepository;
import com.gamenews.news.repository.TopicGameRepository;
import com.gamenews.news.repository.TopicRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TopicGameService {

    private final TopicGameRepository topicGameRepository;
    private final TopicRepository topicRepository;
    private final GameRepository gameRepository;

    @Transactional
    public TopicGameDto.TopicGameResponse linkGame(Long topicId, TopicGameDto.CreateRequest request) {
        Topic topic = findTopicById(topicId);
        Game game = findGameById(request.getGameId());

        if (topicGameRepository.existsByTopic_IdAndGame_Id(topicId, request.getGameId())) {
            throw new IllegalArgumentException("이미 Topic에 연결된 게임입니다: " + request.getGameId());
        }

        TopicGame topicGame = TopicGame.builder()
                .topic(topic)
                .game(game)
                .primary(request.isPrimary())
                .relevanceScore(request.getRelevanceScore())
                .build();

        return TopicGameDto.TopicGameResponse.from(topicGameRepository.save(topicGame));
    }

    public List<TopicGameDto.TopicGameResponse> getGamesByTopic(Long topicId) {
        findTopicById(topicId);

        return topicGameRepository.findAllByTopic_IdOrderByPrimaryDescCreatedAtAsc(topicId).stream()
                .map(TopicGameDto.TopicGameResponse::from)
                .toList();
    }

    private Topic findTopicById(Long topicId) {
        return topicRepository.findById(topicId)
                .orElseThrow(() -> new IllegalArgumentException("Topic을 찾을 수 없습니다: " + topicId));
    }

    private Game findGameById(Long gameId) {
        return gameRepository.findById(gameId)
                .orElseThrow(() -> new IllegalArgumentException("게임을 찾을 수 없습니다: " + gameId));
    }
}
