package com.gamenews.news.repository;

import com.gamenews.news.entity.TopicFranchise;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TopicFranchiseRepository extends JpaRepository<TopicFranchise, Long> {

    boolean existsByTopic_IdAndFranchise_Id(Long topicId, Long franchiseId);

    Optional<TopicFranchise> findByTopic_IdAndFranchise_Id(Long topicId, Long franchiseId);

    List<TopicFranchise> findAllByTopic_IdOrderByPrimaryDescCreatedAtAsc(Long topicId);

    List<TopicFranchise> findAllByFranchise_IdOrderByIdAsc(Long franchiseId);

    long countByFranchise_Id(Long franchiseId);

    @Query("""
            select distinct tf
            from TopicFranchise tf
            join fetch tf.topic t
            join fetch tf.franchise f
            left join fetch f.aliases
            where t.id in :topicIds
            order by t.id asc, tf.primary desc, tf.createdAt asc
            """)
    List<TopicFranchise> findAllWithFranchiseDetailsByTopicIds(@Param("topicIds") List<Long> topicIds);

}
