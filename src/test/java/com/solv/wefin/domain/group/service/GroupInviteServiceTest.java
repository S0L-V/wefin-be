package com.solv.wefin.domain.group.service;

import com.solv.wefin.domain.auth.repository.UserRepository;
import com.solv.wefin.domain.group.dto.GroupInviteInfo;
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

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static com.solv.wefin.domain.group.service.support.GroupTestFixtures.createGroup;
import static com.solv.wefin.domain.group.service.support.GroupTestFixtures.createGroupInvite;
import static com.solv.wefin.domain.group.service.support.GroupTestFixtures.createUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupInviteServiceTest {

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
    @DisplayName("createInviteCode")
    class CreateInviteCodeTest {

        @Test
        @DisplayName("유효한 초대 코드가 없으면 새 초대 코드를 생성한다")
        void createInviteCode_success_create_new_invite() throws Exception {
            UUID userId = UUID.randomUUID();

            Group group = createGroup(1L, "테스트 그룹", GroupType.SHARED);
            var user = createUser(
                    userId,
                    "leader@test.com",
                    "리더",
                    "encoded-password"
            );

            GroupInvite savedInvite = createGroupInvite(
                    10L,
                    group,
                    user,
                    UUID.fromString("550e8400-e29b-41d4-a716-446655440000"),
                    GroupInvite.InviteStatus.PENDING
            );

            when(groupRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(group));
            when(groupMemberRepository.existsByUser_UserIdAndGroupAndStatus(
                    userId,
                    group,
                    GroupMember.GroupMemberStatus.ACTIVE
            )).thenReturn(true);
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(groupInviteRepository.findFirstByGroupAndStatusAndExpiredAtAfterOrderByCreatedAtDesc(
                    eq(group),
                    eq(GroupInvite.InviteStatus.PENDING),
                    any(OffsetDateTime.class)
            )).thenReturn(Optional.empty());
            when(groupInviteRepository.save(any(GroupInvite.class))).thenReturn(savedInvite);

            GroupInviteInfo result = groupService.createInviteCode(1L, userId);

            assertAll(
                    () -> assertThat(result.codeId()).isEqualTo(10L),
                    () -> assertThat(result.groupId()).isEqualTo(1L),
                    () -> assertThat(result.inviteCode())
                            .isEqualTo(UUID.fromString("550e8400-e29b-41d4-a716-446655440000")),
                    () -> assertThat(result.status()).isEqualTo(GroupInvite.InviteStatus.PENDING),
                    () -> assertThat(result.expiredAt()).isNotNull()
            );

            verify(groupRepository).findByIdForUpdate(1L);
            verify(groupMemberRepository).existsByUser_UserIdAndGroupAndStatus(
                    userId,
                    group,
                    GroupMember.GroupMemberStatus.ACTIVE
            );
            verify(userRepository).findById(userId);
            verify(groupInviteRepository).findFirstByGroupAndStatusAndExpiredAtAfterOrderByCreatedAtDesc(
                    eq(group),
                    eq(GroupInvite.InviteStatus.PENDING),
                    any(OffsetDateTime.class)
            );
            verify(groupInviteRepository).save(any(GroupInvite.class));
        }

        @Test
        @DisplayName("유효한 기존 초대 코드가 있으면 재사용한다")
        void createInviteCode_success_reuse_existing_invite() throws Exception {
            UUID userId = UUID.randomUUID();

            Group group = createGroup(1L, "테스트 그룹", GroupType.SHARED);
            var user = createUser(
                    userId,
                    "leader@test.com",
                    "리더",
                    "encoded-password"
            );

            GroupInvite existingInvite = createGroupInvite(
                    10L,
                    group,
                    user,
                    UUID.fromString("550e8400-e29b-41d4-a716-446655440000"),
                    GroupInvite.InviteStatus.PENDING
            );

            when(groupRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(group));
            when(groupMemberRepository.existsByUser_UserIdAndGroupAndStatus(
                    userId,
                    group,
                    GroupMember.GroupMemberStatus.ACTIVE
            )).thenReturn(true);
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(groupInviteRepository.findFirstByGroupAndStatusAndExpiredAtAfterOrderByCreatedAtDesc(
                    eq(group),
                    eq(GroupInvite.InviteStatus.PENDING),
                    any(OffsetDateTime.class)
            )).thenReturn(Optional.of(existingInvite));

            GroupInviteInfo result = groupService.createInviteCode(1L, userId);

            assertAll(
                    () -> assertThat(result.codeId()).isEqualTo(10L),
                    () -> assertThat(result.groupId()).isEqualTo(1L),
                    () -> assertThat(result.inviteCode())
                            .isEqualTo(UUID.fromString("550e8400-e29b-41d4-a716-446655440000")),
                    () -> assertThat(result.status()).isEqualTo(GroupInvite.InviteStatus.PENDING),
                    () -> assertThat(result.expiredAt()).isNotNull()
            );

            verify(groupRepository).findByIdForUpdate(1L);
            verify(groupMemberRepository).existsByUser_UserIdAndGroupAndStatus(
                    userId,
                    group,
                    GroupMember.GroupMemberStatus.ACTIVE
            );
            verify(userRepository).findById(userId);
            verify(groupInviteRepository).findFirstByGroupAndStatusAndExpiredAtAfterOrderByCreatedAtDesc(
                    eq(group),
                    eq(GroupInvite.InviteStatus.PENDING),
                    any(OffsetDateTime.class)
            );
            verify(groupInviteRepository, never()).save(any(GroupInvite.class));
        }

        @Test
        @DisplayName("홈 그룹이면 초대 코드 생성이 불가하다")
        void createInviteCode_fail_when_home_group() throws Exception {
            UUID userId = UUID.randomUUID();
            Group homeGroup = createGroup(1L, "리더의 그룹", GroupType.HOME);

            when(groupRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(homeGroup));

            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> groupService.createInviteCode(1L, userId)
            );

            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.GROUP_HOME_INVITE_NOT_ALLOWED);
            verify(groupMemberRepository, never()).existsByUser_UserIdAndGroupAndStatus(any(), any(), any());
            verify(userRepository, never()).findById(any());
            verify(groupInviteRepository, never()).findFirstByGroupAndStatusAndExpiredAtAfterOrderByCreatedAtDesc(
                    any(), any(), any()
            );
            verify(groupInviteRepository, never()).save(any());
        }

        @Test
        @DisplayName("공유 그룹의 ACTIVE 멤버가 아니면 예외가 발생한다")
        void createInviteCode_fail_when_not_member() throws Exception {
            UUID userId = UUID.randomUUID();
            Group group = createGroup(1L, "테스트 그룹", GroupType.SHARED);

            when(groupRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(group));
            when(groupMemberRepository.existsByUser_UserIdAndGroupAndStatus(
                    userId,
                    group,
                    GroupMember.GroupMemberStatus.ACTIVE
            )).thenReturn(false);

            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> groupService.createInviteCode(1L, userId)
            );

            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.GROUP_INVITE_FORBIDDEN);
            verify(userRepository, never()).findById(any());
            verify(groupInviteRepository, never()).findFirstByGroupAndStatusAndExpiredAtAfterOrderByCreatedAtDesc(
                    any(), any(), any()
            );
            verify(groupInviteRepository, never()).save(any());
        }
    }
}