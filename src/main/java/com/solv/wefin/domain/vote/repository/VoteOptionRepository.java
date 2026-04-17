package com.solv.wefin.domain.vote.repository;

import com.solv.wefin.domain.vote.entity.VoteOption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VoteOptionRepository extends JpaRepository<VoteOption, Long> {
    List<VoteOption> findAllByVote_VoteIdOrderByIdAsc(Long voteId);
    List<VoteOption> findAllByVote_VoteIdInOrderByVote_VoteIdAscIdAsc(List<Long> voteIds);
    List<VoteOption> findAllByIdIn(List<Long> optionIds);
}
