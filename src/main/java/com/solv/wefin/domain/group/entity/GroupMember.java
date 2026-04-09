package com.solv.wefin.domain.group.entity;

import com.solv.wefin.domain.auth.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Entity
@Table(name = "group_member")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GroupMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "group_member_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private GroupMemberRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private GroupMemberStatus status;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private OffsetDateTime joinedAt;

    @Column(name = "left_at")
    private OffsetDateTime leftAt;

    @Builder
    private GroupMember(User user, Group group, GroupMemberRole role, GroupMemberStatus status) {
        this.user = user;
        this.group = group;
        this.role = role;
        this.status = status;
    }

    public static GroupMember createLeader(User user, Group group, GroupMemberStatus status) {
        return new GroupMember(user, group, GroupMemberRole.LEADER, status);
    }

    public static GroupMember createMember(User user, Group group, GroupMemberStatus status) {
        return new GroupMember(user, group, GroupMemberRole.MEMBER, status);
    }

    @PrePersist
    protected void onCreate() {
        if (this.joinedAt == null) {
            this.joinedAt = OffsetDateTime.now();
        }
    }

    public void activate() {
        this.status = GroupMemberStatus.ACTIVE;
        this.leftAt = null;
    }

    public void deactivate() {
        this.status = GroupMemberStatus.INACTIVE;
        this.leftAt = OffsetDateTime.now();
    }

    public boolean isActive() {
        return this.status == GroupMemberStatus.ACTIVE;
    }

    public boolean isLeader() {
        return this.role == GroupMemberRole.LEADER;
    }

    public boolean isHomeGroupMember() {
        return this.group.isHomeGroup();
    }

    public enum GroupMemberRole {
        LEADER,
        MEMBER
    }

    public enum GroupMemberStatus {
        ACTIVE,
        INACTIVE
    }
}