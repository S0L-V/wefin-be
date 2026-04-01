package com.solv.wefin.domain.group.service;

import com.solv.wefin.domain.auth.entity.User;
import com.solv.wefin.domain.group.entity.Group;
import com.solv.wefin.domain.group.entity.GroupMember;
import com.solv.wefin.domain.group.repository.GroupMemberRepository;
import com.solv.wefin.domain.group.repository.GroupRepository;
import com.solv.wefin.domain.group.dto.GroupMemberInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GroupService {
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;

    @Transactional
    public void createDefaultGroup(User user) {
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
    }

    public List<GroupMemberInfo> getActiveMembers(Long groupId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("그룹이 존재하지 않습니다."));

        return groupMemberRepository.findByGroupAndStatusWithUser(
                        group,
                        GroupMember.GroupMemberStatus.ACTIVE
                ).stream()
                .map(GroupMemberInfo::from)
                .toList();
    }
}