package com.gamenews.news.service;

import com.gamenews.news.dto.TopicFranchiseDto;
import com.gamenews.news.entity.Franchise;
import com.gamenews.news.entity.Topic;
import com.gamenews.news.entity.TopicFranchise;
import com.gamenews.news.repository.FranchiseRepository;
import com.gamenews.news.repository.TopicFranchiseRepository;
import com.gamenews.news.repository.TopicRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TopicFranchiseService {

    private final TopicFranchiseRepository topicFranchiseRepository;
    private final TopicRepository topicRepository;
    private final FranchiseRepository franchiseRepository;

    @Transactional
    public TopicFranchiseDto.TopicFranchiseResponse linkFranchise(
            Long topicId,
            TopicFranchiseDto.CreateRequest request) {
        Topic topic = findTopic(topicId);
        Franchise franchise = findFranchise(request.getFranchiseId());

        if (topicFranchiseRepository.existsByTopic_IdAndFranchise_Id(topicId, franchise.getId())) {
            throw new IllegalArgumentException("이미 Topic에 연결된 프랜차이즈입니다: " + franchise.getId());
        }

        TopicFranchise relation = TopicFranchise.builder()
                .topic(topic)
                .franchise(franchise)
                .primary(request.isPrimary())
                .relevanceScore(request.getRelevanceScore())
                .build();

        return TopicFranchiseDto.TopicFranchiseResponse.from(topicFranchiseRepository.save(relation));
    }

    public List<TopicFranchiseDto.TopicFranchiseResponse> getFranchisesByTopic(Long topicId) {
        findTopic(topicId);
        return topicFranchiseRepository.findAllByTopic_IdOrderByPrimaryDescCreatedAtAsc(topicId)
                .stream()
                .map(TopicFranchiseDto.TopicFranchiseResponse::from)
                .toList();
    }

    private Topic findTopic(Long topicId) {
        return topicRepository.findById(topicId)
                .orElseThrow(() -> new IllegalArgumentException("Topic을 찾을 수 없습니다: " + topicId));
    }

    private Franchise findFranchise(Long franchiseId) {
        return franchiseRepository.findById(franchiseId)
                .orElseThrow(() -> new IllegalArgumentException("프랜차이즈를 찾을 수 없습니다: " + franchiseId));
    }
}
