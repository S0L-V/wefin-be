package com.solv.wefin.web.chat.common;

import com.solv.wefin.domain.chat.common.dto.info.ChatUnreadInfo;
import com.solv.wefin.domain.chat.common.service.ChatReadStateService;
import com.solv.wefin.global.common.ApiResponse;
import com.solv.wefin.web.chat.common.dto.response.ChatUnreadResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/chat")
public class ChatReadStateController {

    private final ChatReadStateService chatReadStateService;

    @GetMapping("/unread")
    public ApiResponse<ChatUnreadResponse> getUnread(
            @AuthenticationPrincipal UUID userId
    ) {
        ChatUnreadInfo info = chatReadStateService.getUnreadInfo(userId);
        return ApiResponse.success(ChatUnreadResponse.from(info));
    }
}
