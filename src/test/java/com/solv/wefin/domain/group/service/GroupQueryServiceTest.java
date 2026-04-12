package com.solv.wefin.domain.group.service;

import com.solv.wefin.domain.auth.entity.User;
import com.solv.wefin.domain.auth.repository.UserRepository;
import com.solv.wefin.domain.group.dto.GroupMemberInfo;
import com.solv.wefin.domain.group.dto.MyActiveGroupInfo;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.solv.wefin.domain.group.service.support.GroupTestFixtures.createGroup;
import static com.solv.wefin.domain.group.service.support.GroupTestFixtures.createGroupMember;
import static com.solv.wefin.domain.group.service.support.GroupTestFixtures.createUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupQueryServiceTest {

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
    @DisplayName("getMyActiveGroup")
    class GetMyActiveGroupTest {

        @Test
        @DisplayName("사용자의 현재 활성 그룹을 조회할 수 있다")
        void getMyActiveGroup_success() throws Exception {
            // given
            UUID userId = UUID.randomUUID();

            User user = createUser(
                    userId,
                    "user@example.com",
                    "테스트유저",
                    "password123"
            );

            Group group = createGroup(
                    1L,
                    "테스트유저의 그룹",
                    GroupType.HOME
            );

            GroupMember activeMember = createGroupMember(
                    1L,
                    user,
                    group,
                    GroupMember.GroupMemberRole.LEADER,
                    GroupMember.GroupMemberStatus.ACTIVE
            );

            when(groupMemberRepository.findByUser_UserIdAndStatus(
                    userId,
                    GroupMember.GroupMemberStatus.ACTIVE
            )).thenReturn(Optional.of(activeMember));

            // when
            MyActiveGroupInfo result = groupService.getMyActiveGroup(userId);

            // then
            assertThat(result.groupId()).isEqualTo(1L);
            assertThat(result.groupName()).isEqualTo("테스트유저의 그룹");
            assertThat(result.isHomeGroup()).isTrue();
        }

        @Test
        @DisplayName("활성 그룹이 없으면 예외가 발생한다")
        void getMyActiveGroup_fail_when_active_group_not_found() {
            // given
            UUID userId = UUID.randomUUID();

            when(groupMemberRepository.findByUser_UserIdAndStatus(
                    userId,
                    GroupMember.GroupMemberStatus.ACTIVE
            )).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> groupService.getMyActiveGroup(userId))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage(ErrorCode.GROUP_MEMBER_NOT_FOUND.getMessage());
        }
    }
}