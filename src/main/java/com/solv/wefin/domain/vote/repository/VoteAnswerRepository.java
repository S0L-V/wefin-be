package com.solv.wefin.domain.vote.repository;

import com.solv.wefin.domain.vote.entity.VoteAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface VoteAnswerRepository extends JpaRepository<VoteAnswer, Long> {
    List<VoteAnswer> findAllByVote_VoteIdAndUser_UserId(Long voteId, UUID userId);

    long countByVote_VoteIdAndUser_UserId(Long voteId, UUID userId);

    void deleteAllByVote_VoteIdAndUser_UserId(Long voteId, UUID userId);

    boolean existsByVote_VoteIdAndVoteOption_IdAndUser_UserId(Long voteId, Long optionId, UUID userId);

    @Query("""
    select va.voteOption.id, count(va)
    from VoteAnswer va
    where va.vote.voteId = :voteId
    group by va.voteOption.id
    """)
    List<Object[]> countByVoteIdGroupByOptionId(Long voteId);

    @Query("""
    select count(distinct va.user.userId)
    from VoteAnswer va
    where va.vote.voteId = :voteId
    """)
    long countDistinctUsersByVoteId(Long voteId);

}
