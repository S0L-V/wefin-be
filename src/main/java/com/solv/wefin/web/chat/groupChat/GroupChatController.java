package com.solv.wefin.web.chat.groupChat;

import com.solv.wefin.domain.chat.groupChat.dto.command.ShareNewsCommand;
import com.solv.wefin.domain.chat.groupChat.dto.info.ChatMessagesInfo;
import com.solv.wefin.domain.chat.groupChat.service.ChatMessageService;
import com.solv.wefin.domain.group.entity.Group;
import com.solv.wefin.global.common.ApiResponse;
import com.solv.wefin.web.chat.groupChat.dto.request.ShareNewsRequest;
import com.solv.wefin.web.chat.groupChat.dto.response.GroupChatMessagesResponse;
import com.solv.wefin.web.chat.groupChat.dto.response.GroupChatMetaResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/chat/group")
public class GroupChatController {

    private final ChatMessageService chatMessageService;

    @GetMapping("/messages")
    public ApiResponse<GroupChatMessagesResponse> getRecentMessages(
            @AuthenticationPrincipal UUID userId,
            @RequestParam(required = false) Long beforeMessageId,
            @RequestParam(defaultValue = "30") @Min(1) @Max(100) int size
    ) {
        ChatMessagesInfo info = chatMessageService.getMessages(userId, beforeMessageId, size);

        return ApiResponse.success(GroupChatMessagesResponse.from(info));
    }

    @PostMapping("/news-share")
    public ApiResponse<Void> shareNews (
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody ShareNewsRequest request
    ) {
        chatMessageService.shareNews(
                userId,
                new ShareNewsCommand(request.newsClusterId(), request.replyToMessageId())
        );

        return ApiResponse.success(null);
    }

    @GetMapping("/me")
    public ApiResponse<GroupChatMetaResponse> getMyGroup(
            @AuthenticationPrincipal UUID userId
    ) {
        Group group = chatMessageService.getMyGroup(userId);

        return ApiResponse.success(GroupChatMetaResponse.from(group));
    }
}
