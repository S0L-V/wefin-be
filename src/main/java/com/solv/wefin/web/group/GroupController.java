package com.solv.wefin.web.group;

import com.solv.wefin.domain.group.dto.GroupInviteInfo;
import com.solv.wefin.domain.group.dto.GroupMemberInfo;
import com.solv.wefin.domain.group.service.GroupService;
import com.solv.wefin.global.common.ApiResponse;
import com.solv.wefin.web.group.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/groups")
public class GroupController {

    private final GroupService groupService;

    @GetMapping("/{groupId}/members")
    public ApiResponse<List<GroupMemberResponse>> getMembers(@PathVariable Long groupId) {
        List<GroupMemberResponse> responses = groupService.getActiveMembers(groupId).stream()
                .map(GroupMemberResponse::from)
                .toList();

        return ApiResponse.success(responses);
    }

    @PostMapping
    public ApiResponse<CreateGroupResponse> createGroup(
            @RequestBody @Valid CreateGroupRequest request,
            @AuthenticationPrincipal UUID userId
    ) {
        CreateGroupResponse response = CreateGroupResponse.from(
                groupService.createSharedGroup(userId, request.name())
        );

        return ApiResponse.success(response);
    }

    @PostMapping("/{groupId}/invite-codes")
    public ApiResponse<CreateGroupInviteResponse> createInviteCode(
            @PathVariable Long groupId,
            @AuthenticationPrincipal UUID userId
    ) {
        GroupInviteInfo inviteInfo = groupService.createInviteCode(groupId, userId);
        return ApiResponse.success(CreateGroupInviteResponse.from(inviteInfo));
    }

    @GetMapping("/{groupId}/invite-codes/latest")
    public ApiResponse<CreateGroupInviteResponse> getLatestInviteCode(
            @PathVariable Long groupId,
            @AuthenticationPrincipal UUID userId
    ) {
        GroupInviteInfo info = groupService.getLatestInviteCode(groupId, userId);
        return ApiResponse.success(CreateGroupInviteResponse.from(info));
    }

    @PostMapping("/join")
    public ApiResponse<JoinGroupResponse> joinGroup(
            @RequestBody @Valid JoinGroupRequest request,
            @AuthenticationPrincipal UUID userId
    ) {
        GroupMemberInfo info = groupService.joinGroup(userId, request.inviteCode());
        return ApiResponse.success(
                JoinGroupResponse.from(info)
        );
    }

    @DeleteMapping("/{groupId}/members/me")
    public ApiResponse<LeaveGroupResponse> leaveGroup(
            @PathVariable Long groupId,
            @AuthenticationPrincipal UUID userId
    ) {
        LeaveGroupResponse response = LeaveGroupResponse.from(
                groupService.leaveGroup(groupId, userId)
        );

        return ApiResponse.success(response);
    }
}