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

    @Column(name = "last_read_chat_message_id")
    private Long lastReadChatMessageId;

    @Column(name = "last_read_chat_at")
    private OffsetDateTime lastReadChatAt;

    @Builder
    private GroupMember(User user, Group group, GroupMemberRole role, GroupMemberStatus status) {
        this.user = user;
        this.group = group;
        this.role = role;
        this.status = status;
    }

    public static GroupMember createLeader(User user, Group group) {
        return GroupMember.builder()
                .user(user)
                .group(group)
                .role(GroupMemberRole.LEADER)
                .status(GroupMemberStatus.ACTIVE)
                .build();
    }

    public static GroupMember createMember(User user, Group group) {
        return GroupMember.builder()
                .user(user)
                .group(group)
                .role(GroupMemberRole.MEMBER)
                .status(GroupMemberStatus.ACTIVE)
                .build();
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

    public void changeRoleToLeader() {
        this.role = GroupMemberRole.LEADER;
    }

    public void changeRoleToMember() {
        this.role = GroupMemberRole.MEMBER;
    }

    public void markGroupChatRead(Long messageId) {
        if (messageId == null) {
            this.lastReadChatAt = OffsetDateTime.now();
            return;
        }

        if (this.lastReadChatMessageId == null || messageId >= this.lastReadChatMessageId) {
            this.lastReadChatMessageId = messageId;
            this.lastReadChatAt = OffsetDateTime.now();
        }
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
