package com.solv.wefin.domain.group.service;

import com.solv.wefin.domain.auth.entity.User;
import com.solv.wefin.domain.auth.repository.UserRepository;
import com.solv.wefin.domain.group.dto.GroupInviteInfo;
import com.solv.wefin.domain.group.dto.GroupMemberInfo;
import com.solv.wefin.domain.group.entity.Group;
import com.solv.wefin.domain.group.entity.GroupInvite;
import com.solv.wefin.domain.group.entity.GroupMember;
import com.solv.wefin.domain.group.repository.GroupInviteRepository;
import com.solv.wefin.domain.group.repository.GroupMemberRepository;
import com.solv.wefin.domain.group.repository.GroupRepository;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GroupService {
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupInviteRepository groupInviteRepository;
    private final UserRepository userRepository;

    @Transactional
    public Group createDefaultGroup(User user) {
        Group group = Group.builder()
                .name(user.getNickname() + "의 그룹")
                .build();
        Group savedGroup = groupRepository.save(group);

        GroupMember groupMember = GroupMember.builder()
                .user(user)
                .group(savedGroup)
                .role(GroupMember.GroupMemberRole.LEADER)
                .status(GroupMember.GroupMemberStatus.ACTIVE)
                .build();

        groupMemberRepository.save(groupMember);
        return savedGroup;
    }

    public List<GroupMemberInfo> getActiveMembers(Long groupId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND));

        return groupMemberRepository.findByGroupAndStatusWithUser(
                        group,
                        GroupMember.GroupMemberStatus.ACTIVE
                ).stream()
                .map(GroupMemberInfo::from)
                .toList();
    }

    @Transactional
    public GroupInviteInfo createInviteCode(Long groupId, UUID userId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND));

        boolean isMember = groupMemberRepository.existsByUser_UserIdAndGroupAndStatus(
                userId,
                group,
                GroupMember.GroupMemberStatus.ACTIVE
        );

        if (!isMember) {
            throw new BusinessException(ErrorCode.GROUP_INVITE_FORBIDDEN);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        GroupInvite invite = GroupInvite.create(group, user);
        GroupInvite saved = groupInviteRepository.save(invite);

        return GroupInviteInfo.from(saved);
    }

    @Transactional
    public GroupMemberInfo joinGroup(UUID userId, UUID inviteCode) {
        GroupInvite invite = groupInviteRepository.findByInviteCode(inviteCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_INVITE_NOT_FOUND));

        if (invite.getExpiredAt().isBefore(java.time.OffsetDateTime.now())) {
            invite.expire();
            throw new BusinessException(ErrorCode.GROUP_INVITE_EXPIRED);
        }

        if (invite.getStatus() == GroupInvite.InviteStatus.EXPIRED) {
            throw new BusinessException(ErrorCode.GROUP_INVITE_EXPIRED);
        }

        if (invite.getStatus() == GroupInvite.InviteStatus.ACCEPTED) {
            throw new BusinessException(ErrorCode.GROUP_INVITE_ALREADY_USED);
        }

        Group targetGroup = groupRepository.findByIdForUpdate(invite.getGroup().getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        GroupMember currentActiveMember = groupMemberRepository
                .findByUser_UserIdAndStatus(userId, GroupMember.GroupMemberStatus.ACTIVE)
                .orElse(null);

        if (currentActiveMember != null
                && currentActiveMember.getGroup().getId().equals(targetGroup.getId())) {
            throw new BusinessException(ErrorCode.ALREADY_JOINED_GROUP);
        }

        long activeMemberCount = groupMemberRepository.countByGroupAndStatus(
                targetGroup,
                GroupMember.GroupMemberStatus.ACTIVE
        );

        if (activeMemberCount >= 6) {
            throw new BusinessException(ErrorCode.GROUP_FULL);
        }

        if (currentActiveMember != null) {
            currentActiveMember.leave();
        }

        GroupMember newMember = GroupMember.builder()
                .user(user)
                .group(targetGroup)
                .role(GroupMember.GroupMemberRole.MEMBER)
                .status(GroupMember.GroupMemberStatus.ACTIVE)
                .build();

        groupMemberRepository.save(newMember);
        invite.markAccepted();

        return GroupMemberInfo.from(newMember);
    }
}