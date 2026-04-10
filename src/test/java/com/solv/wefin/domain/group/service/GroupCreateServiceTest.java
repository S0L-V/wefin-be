package com.solv.wefin.domain.group.service;

import com.solv.wefin.domain.auth.repository.UserRepository;
import com.solv.wefin.domain.group.dto.GroupMemberInfo;
import com.solv.wefin.domain.group.entity.Group;
import com.solv.wefin.domain.group.entity.GroupMember;
import com.solv.wefin.domain.group.entity.GroupType;
import com.solv.wefin.domain.group.repository.GroupInviteRepository;
import com.solv.wefin.domain.group.repository.GroupMemberRepository;
import com.solv.wefin.domain.group.repository.GroupRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static com.solv.wefin.domain.group.service.support.GroupTestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GroupCreateServiceTest {

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private GroupMemberRepository groupMemberRepository;

    @Mock
    private GroupInviteRepository groupInviteRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private GroupService groupService;

    @Nested
    @DisplayName("createSharedGroup")
    class CreateSharedGroupTest {

        @Test
        @DisplayName("새 SHARED 그룹을 생성하고 기존 ACTIVE 멤버십을 비활성화한다")
        void createSharedGroup_success() throws Exception {
            // given
            UUID userId = UUID.randomUUID();

            var user = createUser(userId, "test@test.com", "유저", "pw");
            Group homeGroup = createGroup(1L, "홈 그룹", GroupType.HOME);

            GroupMember currentActive = createGroupMember(
                    10L,
                    user,
                    homeGroup,
                    GroupMember.GroupMemberRole.LEADER,
                    GroupMember.GroupMemberStatus.ACTIVE
            );

            Group newGroup = createGroup(100L, "새 그룹", GroupType.SHARED);

            GroupMember newLeader = createGroupMember(
                    20L,
                    user,
                    newGroup,
                    GroupMember.GroupMemberRole.LEADER,
                    GroupMember.GroupMemberStatus.ACTIVE
            );

            when(userRepository.findByIdForUpdate(userId))
                    .thenReturn(Optional.of(user));
            when(groupMemberRepository.findByUser_UserIdAndStatus(
                    userId,
                    GroupMember.GroupMemberStatus.ACTIVE
            )).thenReturn(Optional.of(currentActive));
            when(groupRepository.save(any(Group.class)))
                    .thenReturn(newGroup);
            when(groupMemberRepository.save(any(GroupMember.class)))
                    .thenReturn(newLeader);

            // when
            GroupMemberInfo result = groupService.createSharedGroup(userId, "새 그룹");

            // then
            assertAll(
                    () -> assertThat(result.groupId()).isEqualTo(100L),
                    () -> assertThat(result.groupName()).isEqualTo("새 그룹"),
                    () -> assertThat(result.role()).isEqualTo("LEADER"),
                    () -> assertThat(currentActive.isActive()).isFalse()
            );

            verify(groupRepository).save(any(Group.class));
            verify(groupMemberRepository).save(any(GroupMember.class));
        }

        @Test
        @DisplayName("기존 ACTIVE 그룹이 SHARED면 새 SHARED 그룹을 생성할 수 없다")
        void createSharedGroup_fail_when_current_active_group_is_shared() throws Exception {
            // given
            UUID userId = UUID.randomUUID();

            var user = createUser(userId, "test@test.com", "유저", "pw");
            Group sharedGroup = createGroup(1L, "기존 공유 그룹", GroupType.SHARED);

            GroupMember currentActive = createGroupMember(
                    10L,
                    user,
                    sharedGroup,
                    GroupMember.GroupMemberRole.LEADER,
                    GroupMember.GroupMemberStatus.ACTIVE
            );

            when(userRepository.findByIdForUpdate(userId))
                    .thenReturn(Optional.of(user));
            when(groupMemberRepository.findByUser_UserIdAndStatus(
                    userId,
                    GroupMember.GroupMemberStatus.ACTIVE
            )).thenReturn(Optional.of(currentActive));

            // when & then
            var exception = org.junit.jupiter.api.Assertions.assertThrows(
                    com.solv.wefin.global.error.BusinessException.class,
                    () -> groupService.createSharedGroup(userId, "새 그룹")
            );

            assertThat(exception.getErrorCode()).isEqualTo(com.solv.wefin.global.error.ErrorCode.GROUP_CREATE_REQUIRES_HOME);

            verify(groupRepository, never()).save(any(Group.class));
            verify(groupMemberRepository, never()).save(any(GroupMember.class));
        }
    }
}