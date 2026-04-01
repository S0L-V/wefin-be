package com.solv.wefin.domain.group.service;

import com.solv.wefin.domain.auth.entity.User;
import com.solv.wefin.domain.group.dto.GroupMemberInfo;
import com.solv.wefin.domain.group.entity.Group;
import com.solv.wefin.domain.group.entity.GroupMember;
import com.solv.wefin.domain.group.repository.GroupMemberRepository;
import com.solv.wefin.domain.group.repository.GroupRepository;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GroupServiceTest {

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private GroupMemberRepository groupMemberRepository;

    @InjectMocks
    private GroupService groupService;

    @Nested
    @DisplayName("getActiveMembers")
    class GetActiveMembers {

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
            assertThat(result).hasSize(2);

            assertThat(result.get(0).getUserId()).isEqualTo(leaderUser.getUserId());
            assertThat(result.get(0).getNickname()).isEqualTo("리더");
            assertThat(result.get(0).getRole()).isEqualTo("LEADER");

            assertThat(result.get(1).getUserId()).isEqualTo(memberUser.getUserId());
            assertThat(result.get(1).getNickname()).isEqualTo("멤버");
            assertThat(result.get(1).getRole()).isEqualTo("MEMBER");

            verify(groupRepository).findById(1L);
            verify(groupMemberRepository).findByGroupAndStatusWithUser(
                    group,
                    GroupMember.GroupMemberStatus.ACTIVE
            );
        }

        @Test
        @DisplayName("그룹이 없으면 예외가 발생한다")
        void getActiveMembers_fail_whenGroupNotFound() {
            // given
            when(groupRepository.findById(999L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> groupService.getActiveMembers(999L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("그룹이 존재하지 않습니다.");

            verify(groupRepository).findById(999L);
            verify(groupMemberRepository, never()).findByGroupAndStatusWithUser(any(), any());
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
}