package com.solv.wefin.domain.group.service;

import com.solv.wefin.domain.auth.entity.User;
import com.solv.wefin.domain.group.entity.Group;
import com.solv.wefin.domain.group.entity.GroupMember;
import com.solv.wefin.domain.group.repository.GroupMemberRepository;
import com.solv.wefin.domain.group.repository.GroupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
}