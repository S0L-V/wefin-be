package com.solv.wefin.web.chat.aiChat;

import com.solv.wefin.domain.chat.aiChat.dto.info.AiChatInfo;
import com.solv.wefin.domain.chat.aiChat.service.AiChatService;
import com.solv.wefin.global.common.ApiResponse;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import com.solv.wefin.web.chat.aiChat.dto.request.AiChatRequest;
import com.solv.wefin.web.chat.aiChat.dto.response.AiChatResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/chat/ai")
public class AiChatController {

    private final AiChatService aiChatService;

    @PostMapping("/messages")
    public ApiResponse<AiChatResponse> sendMessage(
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody AiChatRequest request
    ) {

        if (request == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        AiChatInfo info = aiChatService.sendMessage(request.toCommand(), userId);

        return ApiResponse.success(AiChatResponse.from(info));
    }

    @GetMapping("/messages")
    public ApiResponse<List<AiChatResponse>> getMessages(
            @RequestHeader("X-User-Id") UUID userId
    ) {
        List<AiChatResponse> messages = aiChatService.getMessages(userId)
                .stream()
                .map(AiChatResponse::from)
                .toList();

        return ApiResponse.success(messages);
    }
}
