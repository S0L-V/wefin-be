package com.solv.wefin.domain.group.service;

import com.solv.wefin.domain.auth.entity.User;
import com.solv.wefin.domain.auth.repository.UserRepository;
import com.solv.wefin.domain.group.dto.GroupInviteInfo;
import com.solv.wefin.domain.group.dto.GroupMemberInfo;
import com.solv.wefin.domain.group.dto.LeaveGroupInfo;
import com.solv.wefin.domain.group.entity.Group;
import com.solv.wefin.domain.group.entity.GroupInvite;
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

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GroupServiceTest {

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
    @DisplayName("getActiveMembers")
    class GetActiveMembersTest {

        @Test
        @DisplayName("그룹이 존재하면 ACTIVE 멤버 목록을 반환한다")
        void getActiveMembers_success() throws Exception {
            // given
            Group group = createGroup(1L, "테스트 그룹", GroupType.SHARED);

            User leaderUser = createUser(
                    UUID.randomUUID(),
                    "leader@test.com",
                    "리더",
                    "encoded-password"
            );
            User memberUser = createUser(
                    UUID.randomUUID(),
                    "member@test.com",
                    "멤버",
                    "encoded-password"
            );

            GroupMember leader = createGroupMember(
                    1L,
                    leaderUser,
                    group,
                    GroupMember.GroupMemberRole.LEADER,
                    GroupMember.GroupMemberStatus.ACTIVE
            );
            GroupMember member = createGroupMember(
                    2L,
                    memberUser,
                    group,
                    GroupMember.GroupMemberRole.MEMBER,
                    GroupMember.GroupMemberStatus.ACTIVE
            );

            when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
            when(groupMemberRepository.findByGroupAndStatusWithUser(
                    group,
                    GroupMember.GroupMemberStatus.ACTIVE
            )).thenReturn(List.of(leader, member));

            // when
            List<GroupMemberInfo> result = groupService.getActiveMembers(1L);

            // then
            assertAll(
                    () -> assertThat(result).hasSize(2),
                    () -> assertThat(result.get(0).userId()).isEqualTo(leaderUser.getUserId()),
                    () -> assertThat(result.get(0).nickname()).isEqualTo("리더"),
                    () -> assertThat(result.get(0).role()).isEqualTo("LEADER"),
                    () -> assertThat(result.get(1).userId()).isEqualTo(memberUser.getUserId()),
                    () -> assertThat(result.get(1).nickname()).isEqualTo("멤버"),
                    () -> assertThat(result.get(1).role()).isEqualTo("MEMBER")
            );

            verify(groupRepository).findById(1L);
            verify(groupMemberRepository).findByGroupAndStatusWithUser(
                    group,
                    GroupMember.GroupMemberStatus.ACTIVE
            );
        }

        @Test
        @DisplayName("그룹이 없으면 예외가 발생한다")
        void getActiveMembers_fail_when_group_not_found() {
            // given
            when(groupRepository.findById(999L)).thenReturn(Optional.empty());

            // when
            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> groupService.getActiveMembers(999L)
            );

            // then
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.GROUP_NOT_FOUND);
            verify(groupMemberRepository, never()).findByGroupAndStatusWithUser(any(), any());
        }
    }

    @Nested
    @DisplayName("createInviteCode")
    class CreateInviteCodeTest {

        @Test
        @DisplayName("공유 그룹의 ACTIVE 멤버면 초대 코드를 생성한다")
        void createInviteCode_success() throws Exception {
            // given
            UUID userId = UUID.randomUUID();

            Group group = createGroup(1L, "테스트 그룹", GroupType.SHARED);
            User user = createUser(
                    userId,
                    "leader@test.com",
                    "리더",
                    "encoded-password"
            );

            GroupInvite invite = createGroupInvite(
                    10L,
                    group,
                    user,
                    UUID.fromString("550e8400-e29b-41d4-a716-446655440000"),
                    GroupInvite.InviteStatus.PENDING
            );

            when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
            when(groupMemberRepository.existsByUser_UserIdAndGroupAndStatus(
                    userId,
                    group,
                    GroupMember.GroupMemberStatus.ACTIVE
            )).thenReturn(true);
            when(userRepository.findById(userId))
                    .thenReturn(Optional.of(user));
            when(groupInviteRepository.save(any(GroupInvite.class))).thenReturn(invite);

            // when
            GroupInviteInfo result = groupService.createInviteCode(1L, userId);

            // then
            assertAll(
                    () -> assertThat(result.codeId()).isEqualTo(10L),
                    () -> assertThat(result.groupId()).isEqualTo(1L),
                    () -> assertThat(result.inviteCode()).isEqualTo(UUID.fromString("550e8400-e29b-41d4-a716-446655440000")),
                    () -> assertThat(result.status()).isEqualTo(GroupInvite.InviteStatus.PENDING),
                    () -> assertThat(result.expiredAt()).isNotNull()
            );

            verify(groupRepository).findById(1L);
            verify(groupMemberRepository).existsByUser_UserIdAndGroupAndStatus(
                    userId,
                    group,
                    GroupMember.GroupMemberStatus.ACTIVE
            );
            verify(userRepository).findById(userId);
            verify(groupInviteRepository).save(any(GroupInvite.class));
        }

        @Test
        @DisplayName("홈 그룹이면 초대 코드 생성이 불가하다")
        void createInviteCode_fail_when_home_group() throws Exception {
            // given
            UUID userId = UUID.randomUUID();
            Group homeGroup = createGroup(1L, "리더의 그룹", GroupType.HOME);

            when(groupRepository.findById(1L)).thenReturn(Optional.of(homeGroup));

            // when
            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> groupService.createInviteCode(1L, userId)
            );

            // then
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.GROUP_HOME_INVITE_NOT_ALLOWED);
            verify(groupMemberRepository, never()).existsByUser_UserIdAndGroupAndStatus(any(), any(), any());
            verify(userRepository, never()).findById(any());
            verify(groupInviteRepository, never()).save(any());
        }

        @Test
        @DisplayName("공유 그룹의 ACTIVE 멤버가 아니면 예외가 발생한다")
        void createInviteCode_fail_when_not_member() throws Exception {
            // given
            UUID userId = UUID.randomUUID();
            Group group = createGroup(1L, "테스트 그룹", GroupType.SHARED);

            when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
            when(groupMemberRepository.existsByUser_UserIdAndGroupAndStatus(
                    userId,
                    group,
                    GroupMember.GroupMemberStatus.ACTIVE
            )).thenReturn(false);

            // when
            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> groupService.createInviteCode(1L, userId)
            );

            // then
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.GROUP_INVITE_FORBIDDEN);
            verify(userRepository, never()).findById(any());
            verify(groupInviteRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("joinGroup")
    class JoinGroupTest {

        @Test
        @DisplayName("홈 그룹에는 참여할 수 없다")
        void joinGroup_fail_when_home_group() throws Exception {
            // given
            UUID userId = UUID.randomUUID();
            UUID inviteCode = UUID.randomUUID();

            Group homeGroup = createGroup(1L, "홈 그룹", GroupType.HOME);
            User user = createUser(userId, "test@test.com", "유저", "pw");

            GroupInvite invite = createGroupInvite(
                    10L,
                    homeGroup,
                    user,
                    inviteCode,
                    GroupInvite.InviteStatus.PENDING
            );

            when(groupInviteRepository.findByInviteCode(inviteCode))
                    .thenReturn(Optional.of(invite));

            when(groupRepository.findByIdForUpdate(homeGroup.getId()))
                    .thenReturn(Optional.of(homeGroup));

            // when
            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> groupService.joinGroup(userId, inviteCode)
            );

            // then
            assertThat(exception.getErrorCode())
                    .isEqualTo(ErrorCode.GROUP_HOME_JOIN_NOT_ALLOWED);

            verify(userRepository, never()).findById(any());
            verify(groupMemberRepository, never()).findByUser_UserIdAndStatus(any(), any());
            verify(groupMemberRepository, never()).countByGroupAndStatus(any(), any());
            verify(groupMemberRepository, never()).findByUser_UserIdAndGroup_Id(any(), anyLong());
            verify(groupMemberRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("leaveGroup")
    class LeaveGroupTest {

        @Test
        @DisplayName("단체 그룹 탈퇴 시 홈 그룹으로 전환된다")
        void leaveGroup_success() throws Exception {
            // given
            UUID userId = UUID.randomUUID();

            Group sharedGroup = createGroup(1L, "공유 그룹", GroupType.SHARED);
            Group homeGroup = createGroup(100L, "홈 그룹", GroupType.HOME);

            User user = createUser(userId, "test@test.com", "유저", "pw");

            GroupMember leavingMember = createGroupMember(
                    10L,
                    user,
                    sharedGroup,
                    GroupMember.GroupMemberRole.LEADER,
                    GroupMember.GroupMemberStatus.ACTIVE
            );

            GroupMember homeGroupMember = createGroupMember(
                    20L,
                    user,
                    homeGroup,
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
            when(groupMemberRepository.findByUser_UserIdAndGroup_GroupType(userId, GroupType.HOME))
                    .thenReturn(Optional.of(homeGroupMember));

            // when
            LeaveGroupInfo result = groupService.leaveGroup(1L, userId);

            // then
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
            // given
            UUID userId = UUID.randomUUID();
            UUID memberUserId = UUID.randomUUID();

            Group sharedGroup = createGroup(1L, "공유 그룹", GroupType.SHARED);
            Group homeGroup = createGroup(100L, "홈 그룹", GroupType.HOME);

            User leaderUser = createUser(userId, "leader@test.com", "리더", "pw");
            User memberUser = createUser(memberUserId, "member@test.com", "멤버", "pw");

            GroupMember leavingLeader = createGroupMember(
                    10L,
                    leaderUser,
                    sharedGroup,
                    GroupMember.GroupMemberRole.LEADER,
                    GroupMember.GroupMemberStatus.ACTIVE
            );

            GroupMember remainingMember = createGroupMember(
                    11L,
                    memberUser,
                    sharedGroup,
                    GroupMember.GroupMemberRole.MEMBER,
                    GroupMember.GroupMemberStatus.ACTIVE
            );

            GroupMember homeGroupMember = createGroupMember(
                    20L,
                    leaderUser,
                    homeGroup,
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
            when(groupMemberRepository.findByUser_UserIdAndGroup_GroupType(userId, GroupType.HOME))
                    .thenReturn(Optional.of(homeGroupMember));

            // when
            LeaveGroupInfo result = groupService.leaveGroup(1L, userId);

            // then
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
            // given
            UUID userId = UUID.randomUUID();
            Group homeGroup = createGroup(1L, "홈 그룹", GroupType.HOME);

            when(groupRepository.findByIdForUpdate(1L))
                    .thenReturn(Optional.of(homeGroup));

            // when
            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> groupService.leaveGroup(1L, userId)
            );

            // then
            assertThat(exception.getErrorCode())
                    .isEqualTo(ErrorCode.GROUP_HOME_LEAVE_NOT_ALLOWED);

            verify(groupMemberRepository, never()).findByUser_UserIdAndGroup_Id(any(), anyLong());
            verify(groupMemberRepository, never()).findByUser_UserIdAndGroup_GroupType(any(), any());
        }

        @Test
        @DisplayName("이미 비활성화된 그룹 멤버는 탈퇴할 수 없다")
        void leaveGroup_fail_when_member_already_inactive() throws Exception {
            // given
            UUID userId = UUID.randomUUID();

            Group sharedGroup = createGroup(1L, "공유 그룹", GroupType.SHARED);
            User user = createUser(userId, "test@test.com", "유저", "pw");

            GroupMember inactiveMember = createGroupMember(
                    10L,
                    user,
                    sharedGroup,
                    GroupMember.GroupMemberRole.MEMBER,
                    GroupMember.GroupMemberStatus.INACTIVE
            );

            when(groupRepository.findByIdForUpdate(1L))
                    .thenReturn(Optional.of(sharedGroup));
            when(groupMemberRepository.findByUser_UserIdAndGroup_Id(userId, 1L))
                    .thenReturn(Optional.of(inactiveMember));

            // when
            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> groupService.leaveGroup(1L, userId)
            );

            // then
            assertThat(exception.getErrorCode())
                    .isEqualTo(ErrorCode.GROUP_MEMBER_ALREADY_INACTIVE);

            verify(groupMemberRepository, never()).countByGroupAndStatus(any(), any());
            verify(groupMemberRepository, never()).findByUser_UserIdAndGroup_GroupType(any(), any());
        }

        @Test
        @DisplayName("홈 그룹 멤버십이 없으면 홈 그룹을 새로 생성한 뒤 전환한다")
        void leaveGroup_success_when_home_membership_missing() throws Exception {
            // given
            UUID userId = UUID.randomUUID();

            Group sharedGroup = createGroup(1L, "공유 그룹", GroupType.SHARED);
            Group createdHomeGroup = createGroup(100L, "유저의 그룹", GroupType.HOME);

            User user = createUser(userId, "test@test.com", "유저", "pw");

            GroupMember leavingMember = createGroupMember(
                    10L,
                    user,
                    sharedGroup,
                    GroupMember.GroupMemberRole.LEADER,
                    GroupMember.GroupMemberStatus.ACTIVE
            );

            GroupMember createdHomeGroupMember = createGroupMember(
                    20L,
                    user,
                    createdHomeGroup,
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
            when(groupMemberRepository.findByUser_UserIdAndGroup_GroupType(userId, GroupType.HOME))
                    .thenReturn(Optional.empty())
                    .thenReturn(Optional.of(createdHomeGroupMember));
            when(groupRepository.save(any(Group.class)))
                    .thenReturn(createdHomeGroup);
            when(groupMemberRepository.save(any(GroupMember.class)))
                    .thenReturn(createdHomeGroupMember);

            // when
            LeaveGroupInfo result = groupService.leaveGroup(1L, userId);

            // then
            assertAll(
                    () -> assertThat(result.leftGroupId()).isEqualTo(1L),
                    () -> assertThat(result.currentGroupId()).isEqualTo(100L),
                    () -> assertThat(leavingMember.isActive()).isFalse(),
                    () -> assertThat(leavingMember.isLeader()).isFalse(),
                    () -> assertThat(createdHomeGroupMember.isActive()).isTrue()
            );

            verify(groupRepository).save(any(Group.class));
            verify(groupMemberRepository).save(any(GroupMember.class));
        }
    }

    private Group createGroup(Long id, String name, GroupType groupType) throws Exception {
        Constructor<Group> constructor = Group.class.getDeclaredConstructor(String.class, GroupType.class);
        constructor.setAccessible(true);
        Group group = constructor.newInstance(name, groupType);

        Field idField = Group.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(group, id);

        return group;
    }

    private User createUser(UUID userId, String email, String nickname, String password) throws Exception {
        Constructor<User> constructor = User.class.getDeclaredConstructor(String.class, String.class, String.class);
        constructor.setAccessible(true);
        User user = constructor.newInstance(email, nickname, password);

        Field userIdField = User.class.getDeclaredField("userId");
        userIdField.setAccessible(true);
        userIdField.set(user, userId);

        return user;
    }

    private GroupMember createGroupMember(
            Long id,
            User user,
            Group group,
            GroupMember.GroupMemberRole role,
            GroupMember.GroupMemberStatus status
    ) throws Exception {
        Constructor<GroupMember> constructor = GroupMember.class.getDeclaredConstructor(
                User.class,
                Group.class,
                GroupMember.GroupMemberRole.class,
                GroupMember.GroupMemberStatus.class
        );
        constructor.setAccessible(true);
        GroupMember groupMember = constructor.newInstance(user, group, role, status);

        Field idField = GroupMember.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(groupMember, id);

        return groupMember;
    }

    private GroupInvite createGroupInvite(
            Long id,
            Group group,
            User createdBy,
            UUID inviteCode,
            GroupInvite.InviteStatus status
    ) throws Exception {
        Constructor<GroupInvite> constructor = GroupInvite.class.getDeclaredConstructor(
                Group.class,
                User.class,
                UUID.class,
                GroupInvite.InviteStatus.class,
                OffsetDateTime.class
        );
        constructor.setAccessible(true);

        GroupInvite groupInvite = constructor.newInstance(
                group,
                createdBy,
                inviteCode,
                status,
                OffsetDateTime.now().plusHours(24)
        );

        Field idField = GroupInvite.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(groupInvite, id);

        return groupInvite;
    }
}