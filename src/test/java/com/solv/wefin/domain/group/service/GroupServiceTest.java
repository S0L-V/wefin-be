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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
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
            Group group = createGroup(1L, "테스트 그룹");

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
                    () -> assertThat(result.get(0).getUserId()).isEqualTo(leaderUser.getUserId()),
                    () -> assertThat(result.get(0).getNickname()).isEqualTo("리더"),
                    () -> assertThat(result.get(0).getRole()).isEqualTo("LEADER"),
                    () -> assertThat(result.get(1).getUserId()).isEqualTo(memberUser.getUserId()),
                    () -> assertThat(result.get(1).getNickname()).isEqualTo("멤버"),
                    () -> assertThat(result.get(1).getRole()).isEqualTo("MEMBER")
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
        @DisplayName("그룹의 ACTIVE 멤버면 초대 코드를 생성한다")
        void createInviteCode_success() throws Exception {
            // given
            UUID userId = UUID.randomUUID();

            Group group = createGroup(1L, "테스트 그룹");
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
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(groupInviteRepository.save(any(GroupInvite.class))).thenReturn(invite);

            // when
            GroupInviteInfo result = groupService.createInviteCode(1L, userId);

            // then
            assertAll(
                    () -> assertThat(result.getCodeId()).isEqualTo(10L),
                    () -> assertThat(result.getGroupId()).isEqualTo(1L),
                    () -> assertThat(result.getInviteCode()).isEqualTo(UUID.fromString("550e8400-e29b-41d4-a716-446655440000")),
                    () -> assertThat(result.getStatus()).isEqualTo("PENDING"),
                    () -> assertThat(result.getExpiredAt()).isNotNull()
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
        @DisplayName("그룹의 멤버가 아니면 예외가 발생한다")
        void createInviteCode_fail_when_not_member() {
            // given
            UUID userId = UUID.randomUUID();
            Group group = mock(Group.class);

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

    private Group createGroup(Long id, String name) throws Exception {
        Constructor<Group> constructor = Group.class.getDeclaredConstructor(String.class);
        constructor.setAccessible(true);
        Group group = constructor.newInstance(name);

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
                java.time.OffsetDateTime.class
        );
        constructor.setAccessible(true);

        GroupInvite groupInvite = constructor.newInstance(
                group,
                createdBy,
                inviteCode,
                status,
                java.time.OffsetDateTime.now().plusHours(24)
        );

        Field idField = GroupInvite.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(groupInvite, id);

        return groupInvite;
    }
}