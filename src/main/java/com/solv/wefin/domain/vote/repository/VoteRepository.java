package com.solv.wefin.domain.vote.repository;

import com.solv.wefin.domain.vote.entity.Vote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;


public interface VoteRepository extends JpaRepository<Vote, Long> {
    Optional<Vote> findById(Long voteId);
    List<Vote> findAllByGroup_IdOrderByVoteIdDesc(Long groupId);
    List<Vote> findAllByVoteIdIn(List<Long> voteIds);
}
