package com.solv.wefin.domain.vote.entity;

import com.solv.wefin.domain.auth.entity.User;
import com.solv.wefin.domain.group.entity.Group;
import com.solv.wefin.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Entity
@Table(name = "vote")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Vote extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "vote_id")
    private Long voteId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VoteStatus status;

    @Column(name = "ends_at")
    private OffsetDateTime endsAt;

    @Column(name = "max_select_count", nullable = false)
    private int maxSelectCount;

    @Builder
    private Vote(Group group, User createdBy, String title, VoteStatus status, OffsetDateTime endsAt, int maxSelectCount) {
        this.group = group;
        this.createdBy = createdBy;
        this.title = title;
        this.status = status;
        this.endsAt = endsAt;
        this.maxSelectCount = maxSelectCount;
    }

    public static Vote create(Group group, User createdBy, String title, OffsetDateTime endsAt, int maxSelectCount) {
        return Vote.builder()
                .group(group)
                .createdBy(createdBy)
                .title(title)
                .status(VoteStatus.OPEN)
                .endsAt(endsAt)
                .maxSelectCount(maxSelectCount)
                .build();
    }

    public void close() {
        this.status = VoteStatus.CLOSED;
    }
}
