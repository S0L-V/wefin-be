package com.solv.wefin.web.chat.aiChat;

import com.solv.wefin.domain.chat.aiChat.dto.info.AiChatInfo;
import com.solv.wefin.domain.chat.aiChat.service.AiChatService;
import com.solv.wefin.global.common.ApiResponse;
import com.solv.wefin.web.chat.aiChat.dto.request.AiChatRequest;
import com.solv.wefin.web.chat.aiChat.dto.response.AiChatResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/chat/ai")
public class AiChatController {

    private final AiChatService aiChatService;

    @PostMapping("/messages")
    public ApiResponse<AiChatResponse> sendMessage(@RequestBody AiChatRequest request) {

        AiChatInfo info = aiChatService.sendMessage(request.toCommand());

        return ApiResponse.success(AiChatResponse.from(info));
    }
}
