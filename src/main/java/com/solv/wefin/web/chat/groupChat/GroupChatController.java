package com.solv.wefin.web.chat.groupChat;

import com.solv.wefin.domain.chat.groupChat.service.ChatMessageService;
import com.solv.wefin.domain.group.entity.Group;
import com.solv.wefin.global.common.ApiResponse;
import com.solv.wefin.web.chat.groupChat.dto.response.ChatMessageResponse;
import com.solv.wefin.web.chat.groupChat.dto.response.GroupChatMetaResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/chat/group")
public class GroupChatController {

    private final ChatMessageService chatMessageService;

    @GetMapping("/messages")
    public ApiResponse<List<ChatMessageResponse>> getRecentMessages(
            @AuthenticationPrincipal UUID userId,
            @RequestParam(defaultValue = "50") int limit) {
        List<ChatMessageResponse> messages = chatMessageService.getRecentMessages(userId, limit)
                .stream()
                .map(ChatMessageResponse::from)
                .toList();

        return ApiResponse.success(messages);
    }

    @GetMapping("/me")
    public ApiResponse<GroupChatMetaResponse> getMyGroup(
            @AuthenticationPrincipal UUID userId
    ) {
        Group group = chatMessageService.getMyGroup(userId);

        return ApiResponse.success(GroupChatMetaResponse.from(group));
    }
}
