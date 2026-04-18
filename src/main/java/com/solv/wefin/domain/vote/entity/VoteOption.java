package com.solv.wefin.domain.vote.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Entity
@Table(name = "vote_option")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VoteOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "option_id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vote_id", nullable = false)
    private Vote vote;

    @Column(name = "option_text", nullable = false)
    private String optionText;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Builder
    private VoteOption(Vote vote, String optionText, OffsetDateTime createdAt) {
        this.vote = vote;
        this.optionText = optionText;
        this.createdAt = createdAt;
    }

    public static VoteOption create(Vote vote, String optionText) {

        return VoteOption.builder()
                .vote(vote)
                .optionText(optionText)
                .createdAt(OffsetDateTime.now())
                .build();
    }
}
