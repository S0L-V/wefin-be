package com.solv.wefin.domain.group.entity;

import com.solv.wefin.domain.auth.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "group_invite")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GroupInvite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "code_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(name = "invite_code", nullable = false, unique = true)
    private UUID inviteCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private InviteStatus status;

    @Column(name = "expired_at", nullable = false)
    private OffsetDateTime expiredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Builder
    private GroupInvite(Group group, User createdBy, UUID inviteCode,
                        InviteStatus status, OffsetDateTime expiredAt) {
        this.group = group;
        this.createdBy = createdBy;
        this.inviteCode = inviteCode;
        this.status = status;
        this.expiredAt = expiredAt;
    }

    public static GroupInvite create(Group group, User createdBy) {
        return GroupInvite.builder()
                .group(group)
                .createdBy(createdBy)
                .inviteCode(UUID.randomUUID())
                .status(InviteStatus.PENDING)
                .expiredAt(OffsetDateTime.now().plusHours(24))
                .build();
    }

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = OffsetDateTime.now();
        }
    }

    public boolean isExpired(OffsetDateTime now) {
        return this.expiredAt.isBefore(now) || this.status == InviteStatus.EXPIRED;
    }

    public void expire() {
        this.status = InviteStatus.EXPIRED;
    }

    public enum InviteStatus {
        PENDING,
        ACCEPTED,
        EXPIRED
    }
}