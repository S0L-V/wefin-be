package com.solv.wefin.domain.vote.entity;


import com.solv.wefin.domain.auth.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Entity
@Table(
        name = "vote_answer",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_vote_answer_user_option",
                columnNames = {"user_id", "option_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VoteAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "answer_id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "option_id", nullable = false)
    private VoteOption voteOption;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vote_id", nullable = false)
    private Vote vote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Builder
    private VoteAnswer(VoteOption voteOption, Vote vote, User user, OffsetDateTime createdAt) {
        this.voteOption = voteOption;
        this.vote = vote;
        this.user = user;
        this.createdAt = createdAt;
    }

    public static VoteAnswer voted(VoteOption voteOption, Vote vote, User user) {

        return VoteAnswer.builder()
                .voteOption(voteOption)
                .vote(vote)
                .user(user)
                .createdAt(OffsetDateTime.now())
                .build();
    }
}
