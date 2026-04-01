package com.solv.wefin.web.group;

import com.solv.wefin.domain.group.service.GroupService;
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
    public List<GroupMemberResponse> getGroupMembers(@PathVariable Long groupId) {
        return groupService.getActiveMembers(groupId).stream()
                .map(GroupMemberResponse::from)
                .toList();
    }
}