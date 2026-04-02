package com.solv.wefin.web.group;

import com.solv.wefin.domain.group.service.GroupService;
import com.solv.wefin.global.common.ApiResponse;
import com.solv.wefin.web.group.dto.GroupMemberResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
}