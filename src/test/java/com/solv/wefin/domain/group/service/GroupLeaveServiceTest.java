package com.solv.wefin.domain.group.service;

import com.solv.wefin.domain.auth.repository.UserRepository;
import com.solv.wefin.domain.group.dto.LeaveGroupInfo;
import com.solv.wefin.domain.group.entity.Group;
import com.solv.wefin.domain.group.entity.GroupMember;
import com.solv.wefin.domain.group.entity.GroupType;
import com.solv.wefin.domain.group.repository.GroupInviteRepository;
import com.solv.wefin.domain.group.repository.GroupMemberRepository;
import com.solv.wefin.domain.group.repository.GroupRepository;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static com.solv.wefin.domain.group.service.support.GroupTestFixtures.createGroup;
import static com.solv.wefin.domain.group.service.support.GroupTestFixtures.createGroupMember;
import static com.solv.wefin.domain.group.service.support.GroupTestFixtures.createUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupLeaveServiceTest {

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
    @DisplayName("leaveGroup")
    class LeaveGroupTest {

        @Test
        @DisplayName("단체 그룹 탈퇴 시 홈 그룹으로 전환된다")
        void leaveGroup_success() throws Exception {
            UUID userId = UUID.randomUUID();

            Group sharedGroup = createGroup(1L, "공유 그룹", GroupType.SHARED);
            Group homeGroup = createGroup(100L, "홈 그룹", GroupType.HOME);

            var user = createUser(userId, "test@test.com", "유저", "pw");
            user.setHomeGroup(homeGroup);

            GroupMember leavingMember = createGroupMember(
                    10L, user, sharedGroup,
                    GroupMember.GroupMemberRole.LEADER,
                    GroupMember.GroupMemberStatus.ACTIVE
            );

            GroupMember homeGroupMember = createGroupMember(
                    20L, user, homeGroup,
                    GroupMember.GroupMemberRole.LEADER,
                    GroupMember.GroupMemberStatus.INACTIVE
            );

            when(groupRepository.findByIdForUpdate(1L))
                    .thenReturn(Optional.of(sharedGroup));
            when(groupMemberRepository.findByUser_UserIdAndGroup_Id(userId, 1L))
                    .thenReturn(Optional.of(leavingMember));
            when(groupMemberRepository.countByGroupAndStatus(
                    sharedGroup,
                    GroupMember.GroupMemberStatus.ACTIVE
            )).thenReturn(0L);
            when(userRepository.findByIdForUpdate(userId))
                    .thenReturn(Optional.of(user));
            when(groupRepository.findById(homeGroup.getId()))
                    .thenReturn(Optional.of(homeGroup));
            when(groupMemberRepository.findByUser_UserIdAndGroup_Id(userId, homeGroup.getId()))
                    .thenReturn(Optional.of(homeGroupMember));

            LeaveGroupInfo result = groupService.leaveGroup(1L, userId);

            assertAll(
                    () -> assertThat(result.leftGroupId()).isEqualTo(1L),
                    () -> assertThat(result.currentGroupId()).isEqualTo(100L),
                    () -> assertThat(leavingMember.isActive()).isFalse(),
                    () -> assertThat(leavingMember.isLeader()).isFalse(),
                    () -> assertThat(homeGroupMember.isActive()).isTrue()
            );
        }

        @Test
        @DisplayName("리더가 탈퇴하면 남은 ACTIVE 멤버에게 리더를 위임한다")
        void leaveGroup_success_when_leader_transfers_leadership() throws Exception {
            UUID userId = UUID.randomUUID();
            UUID memberUserId = UUID.randomUUID();

            Group sharedGroup = createGroup(1L, "공유 그룹", GroupType.SHARED);
            Group homeGroup = createGroup(100L, "홈 그룹", GroupType.HOME);

            var leaderUser = createUser(userId, "leader@test.com", "리더", "pw");
            leaderUser.setHomeGroup(homeGroup);
            var memberUser = createUser(memberUserId, "member@test.com", "멤버", "pw");

            GroupMember leavingLeader = createGroupMember(
                    10L, leaderUser, sharedGroup,
                    GroupMember.GroupMemberRole.LEADER,
                    GroupMember.GroupMemberStatus.ACTIVE
            );

            GroupMember remainingMember = createGroupMember(
                    11L, memberUser, sharedGroup,
                    GroupMember.GroupMemberRole.MEMBER,
                    GroupMember.GroupMemberStatus.ACTIVE
            );

            GroupMember homeGroupMember = createGroupMember(
                    20L, leaderUser, homeGroup,
                    GroupMember.GroupMemberRole.LEADER,
                    GroupMember.GroupMemberStatus.INACTIVE
            );

            when(groupRepository.findByIdForUpdate(1L))
                    .thenReturn(Optional.of(sharedGroup));
            when(groupMemberRepository.findByUser_UserIdAndGroup_Id(userId, 1L))
                    .thenReturn(Optional.of(leavingLeader));
            when(groupMemberRepository.countByGroupAndStatus(
                    sharedGroup,
                    GroupMember.GroupMemberStatus.ACTIVE
            )).thenReturn(1L);
            when(userRepository.findByIdForUpdate(userId))
                    .thenReturn(Optional.of(leaderUser));
            when(groupMemberRepository.findFirstByGroupAndStatusAndUser_UserIdNotOrderByIdAsc(
                    sharedGroup,
                    GroupMember.GroupMemberStatus.ACTIVE,
                    userId
            )).thenReturn(Optional.of(remainingMember));
            when(groupRepository.findById(homeGroup.getId()))
                    .thenReturn(Optional.of(homeGroup));
            when(groupMemberRepository.findByUser_UserIdAndGroup_Id(userId, homeGroup.getId()))
                    .thenReturn(Optional.of(homeGroupMember));

            LeaveGroupInfo result = groupService.leaveGroup(1L, userId);

            assertAll(
                    () -> assertThat(result.leftGroupId()).isEqualTo(1L),
                    () -> assertThat(result.currentGroupId()).isEqualTo(100L),
                    () -> assertThat(leavingLeader.isActive()).isFalse(),
                    () -> assertThat(leavingLeader.isLeader()).isFalse(),
                    () -> assertThat(homeGroupMember.isActive()).isTrue(),
                    () -> assertThat(remainingMember.isLeader()).isTrue()
            );
        }

        @Test
        @DisplayName("홈 그룹은 탈퇴할 수 없다")
        void leaveGroup_fail_when_home_group() throws Exception {
            UUID userId = UUID.randomUUID();
            Group homeGroup = createGroup(1L, "홈 그룹", GroupType.HOME);

            when(groupRepository.findByIdForUpdate(1L))
                    .thenReturn(Optional.of(homeGroup));

            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> groupService.leaveGroup(1L, userId)
            );

            assertThat(exception.getErrorCode())
                    .isEqualTo(ErrorCode.GROUP_HOME_LEAVE_NOT_ALLOWED);

            verify(groupMemberRepository, never()).findByUser_UserIdAndGroup_Id(any(), anyLong());
        }

        @Test
        @DisplayName("이미 비활성화된 그룹 멤버는 탈퇴할 수 없다")
        void leaveGroup_fail_when_member_already_inactive() throws Exception {
            UUID userId = UUID.randomUUID();

            Group sharedGroup = createGroup(1L, "공유 그룹", GroupType.SHARED);
            var user = createUser(userId, "test@test.com", "유저", "pw");

            GroupMember inactiveMember = createGroupMember(
                    10L, user, sharedGroup,
                    GroupMember.GroupMemberRole.MEMBER,
                    GroupMember.GroupMemberStatus.INACTIVE
            );

            when(groupRepository.findByIdForUpdate(1L))
                    .thenReturn(Optional.of(sharedGroup));
            when(groupMemberRepository.findByUser_UserIdAndGroup_Id(userId, 1L))
                    .thenReturn(Optional.of(inactiveMember));

            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> groupService.leaveGroup(1L, userId)
            );

            assertThat(exception.getErrorCode())
                    .isEqualTo(ErrorCode.GROUP_MEMBER_ALREADY_INACTIVE);

            verify(groupMemberRepository, never()).countByGroupAndStatus(any(), any());
        }

        @Test
        @DisplayName("홈 그룹 자체가 없으면 홈 그룹을 새로 생성한 뒤 전환한다")
        void leaveGroup_success_when_home_group_missing() throws Exception {
            UUID userId = UUID.randomUUID();

            Group sharedGroup = createGroup(1L, "공유 그룹", GroupType.SHARED);
            Group createdHomeGroup = createGroup(100L, "유저의 그룹", GroupType.HOME);

            var user = createUser(userId, "test@test.com", "유저", "pw");

            GroupMember leavingMember = createGroupMember(
                    10L, user, sharedGroup,
                    GroupMember.GroupMemberRole.LEADER,
                    GroupMember.GroupMemberStatus.ACTIVE
            );

            GroupMember createdHomeGroupMember = createGroupMember(
                    20L, user, createdHomeGroup,
                    GroupMember.GroupMemberRole.LEADER,
                    GroupMember.GroupMemberStatus.ACTIVE
            );

            when(groupRepository.findByIdForUpdate(1L))
                    .thenReturn(Optional.of(sharedGroup));
            when(groupMemberRepository.findByUser_UserIdAndGroup_Id(userId, 1L))
                    .thenReturn(Optional.of(leavingMember));
            when(groupMemberRepository.countByGroupAndStatus(
                    sharedGroup,
                    GroupMember.GroupMemberStatus.ACTIVE
            )).thenReturn(0L);
            when(userRepository.findByIdForUpdate(userId))
                    .thenReturn(Optional.of(user));
            when(groupRepository.save(any(Group.class)))
                    .thenReturn(createdHomeGroup);
            when(groupMemberRepository.save(any(GroupMember.class)))
                    .thenReturn(createdHomeGroupMember);

            LeaveGroupInfo result = groupService.leaveGroup(1L, userId);

            assertAll(
                    () -> assertThat(result.leftGroupId()).isEqualTo(1L),
                    () -> assertThat(result.currentGroupId()).isEqualTo(100L),
                    () -> assertThat(leavingMember.isActive()).isFalse(),
                    () -> assertThat(leavingMember.isLeader()).isFalse(),
                    () -> assertThat(createdHomeGroupMember.isActive()).isTrue(),
                    () -> assertThat(user.getHomeGroup()).isEqualTo(createdHomeGroup)
            );

            verify(groupRepository).save(any(Group.class));
            verify(groupMemberRepository).save(any(GroupMember.class));
        }

        @Test
        @DisplayName("홈 그룹은 있지만 홈 멤버십이 없으면 멤버십만 새로 생성해 전환한다")
        void leaveGroup_success_when_home_membership_missing() throws Exception {
            UUID userId = UUID.randomUUID();

            Group sharedGroup = createGroup(1L, "공유 그룹", GroupType.SHARED);
            Group homeGroup = createGroup(100L, "홈 그룹", GroupType.HOME);

            var user = createUser(userId, "test@test.com", "유저", "pw");
            user.setHomeGroup(homeGroup);

            GroupMember leavingMember = createGroupMember(
                    10L, user, sharedGroup,
                    GroupMember.GroupMemberRole.LEADER,
                    GroupMember.GroupMemberStatus.ACTIVE
            );

            GroupMember createdHomeGroupMember = createGroupMember(
                    20L, user, homeGroup,
                    GroupMember.GroupMemberRole.LEADER,
                    GroupMember.GroupMemberStatus.ACTIVE
            );

            when(groupRepository.findByIdForUpdate(1L))
                    .thenReturn(Optional.of(sharedGroup));
            when(groupMemberRepository.findByUser_UserIdAndGroup_Id(userId, 1L))
                    .thenReturn(Optional.of(leavingMember));
            when(groupMemberRepository.countByGroupAndStatus(
                    sharedGroup,
                    GroupMember.GroupMemberStatus.ACTIVE
            )).thenReturn(0L);
            when(userRepository.findByIdForUpdate(userId))
                    .thenReturn(Optional.of(user));
            when(groupRepository.findById(homeGroup.getId()))
                    .thenReturn(Optional.of(homeGroup));
            when(groupMemberRepository.findByUser_UserIdAndGroup_Id(userId, homeGroup.getId()))
                    .thenReturn(Optional.empty());
            when(groupMemberRepository.save(any(GroupMember.class)))
                    .thenReturn(createdHomeGroupMember);

            LeaveGroupInfo result = groupService.leaveGroup(1L, userId);

            assertAll(
                    () -> assertThat(result.leftGroupId()).isEqualTo(1L),
                    () -> assertThat(result.currentGroupId()).isEqualTo(100L),
                    () -> assertThat(leavingMember.isActive()).isFalse(),
                    () -> assertThat(leavingMember.isLeader()).isFalse(),
                    () -> assertThat(createdHomeGroupMember.isActive()).isTrue()
            );

            verify(groupRepository, never()).save(any(Group.class));
            verify(groupMemberRepository).save(any(GroupMember.class));
        }
    }
}