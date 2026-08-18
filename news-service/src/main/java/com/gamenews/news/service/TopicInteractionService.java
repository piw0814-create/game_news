package com.gamenews.news.service;

import com.gamenews.news.dto.TopicInteractionDto;
import com.gamenews.news.entity.Topic;
import com.gamenews.news.entity.TopicComment;
import com.gamenews.news.entity.TopicLike;
import com.gamenews.news.repository.TopicCommentRepository;
import com.gamenews.news.repository.TopicLikeRepository;
import com.gamenews.news.repository.TopicRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TopicInteractionService {

    private final TopicRepository topicRepository;
    private final TopicCommentRepository topicCommentRepository;
    private final TopicLikeRepository topicLikeRepository;

    public List<TopicInteractionDto.CommentResponse> getComments(Long topicId, Long userId) {
        requireTopic(topicId);
        return topicCommentRepository.findAllByTopic_IdOrderByCreatedAtAsc(topicId).stream()
                .map(comment -> TopicInteractionDto.CommentResponse.from(comment, userId))
                .toList();
    }

    @Transactional
    public TopicInteractionDto.CommentResponse createComment(
            Long topicId,
            Long userId,
            String email,
            TopicInteractionDto.CommentCreateRequest request) {

        Topic topic = requireTopic(topicId);

        TopicComment comment = TopicComment.builder()
                .topic(topic)
                .userId(userId)
                .authorName(toAuthorName(email, userId))
                .content(request.getContent().trim())
                .build();

        return TopicInteractionDto.CommentResponse.from(
                topicCommentRepository.save(comment),
                userId
        );
    }

    @Transactional
    public void deleteComment(Long topicId, Long commentId, Long userId) {
        TopicComment comment = topicCommentRepository.findByIdAndTopic_Id(commentId, topicId)
                .orElseThrow(() -> new IllegalArgumentException("댓글을 찾을 수 없습니다"));

        if (!comment.getUserId().equals(userId)) {
            throw new IllegalArgumentException("본인 댓글만 삭제할 수 있습니다");
        }

        topicCommentRepository.delete(comment);
    }

    public TopicInteractionDto.LikeStatusResponse getLikeStatus(Long topicId, Long userId) {
        requireTopic(topicId);
        return buildLikeStatus(topicId, userId);
    }

    @Transactional
    public TopicInteractionDto.LikeStatusResponse like(Long topicId, Long userId) {
        Topic topic = requireTopic(topicId);

        if (!topicLikeRepository.existsByTopic_IdAndUserId(topicId, userId)) {
            topicLikeRepository.save(TopicLike.builder()
                    .topic(topic)
                    .userId(userId)
                    .build());
        }

        return buildLikeStatus(topicId, userId);
    }

    @Transactional
    public TopicInteractionDto.LikeStatusResponse unlike(Long topicId, Long userId) {
        requireTopic(topicId);
        topicLikeRepository.deleteByTopic_IdAndUserId(topicId, userId);
        return buildLikeStatus(topicId, userId);
    }

    private Topic requireTopic(Long topicId) {
        return topicRepository.findById(topicId)
                .orElseThrow(() -> new IllegalArgumentException("Topic을 찾을 수 없습니다"));
    }

    private TopicInteractionDto.LikeStatusResponse buildLikeStatus(Long topicId, Long userId) {
        return TopicInteractionDto.LikeStatusResponse.builder()
                .count(topicLikeRepository.countByTopic_Id(topicId))
                .liked(topicLikeRepository.existsByTopic_IdAndUserId(topicId, userId))
                .build();
    }

    private String toAuthorName(String email, Long userId) {
        if (email != null && !email.isBlank()) {
            int atIndex = email.indexOf('@');
            String value = atIndex > 0 ? email.substring(0, atIndex) : email;
            if (!value.isBlank()) {
                return value.length() <= 100 ? value : value.substring(0, 100);
            }
        }
        return "user-" + userId;
    }
}
