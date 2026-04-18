package com.solv.wefin.web.chat.aiChat;

import com.solv.wefin.domain.chat.aiChat.dto.info.AiChatInfo;
import com.solv.wefin.domain.chat.aiChat.dto.info.AiChatMessagesInfo;
import com.solv.wefin.domain.chat.aiChat.service.AiChatService;
import com.solv.wefin.global.common.ApiResponse;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import com.solv.wefin.web.chat.aiChat.dto.request.AiChatRequest;
import com.solv.wefin.web.chat.aiChat.dto.response.AiChatMessagesResponse;
import com.solv.wefin.web.chat.aiChat.dto.response.AiChatResponse;
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
@RequestMapping("/api/chat/ai")
public class AiChatController {

    private final AiChatService aiChatService;

    @PostMapping("/messages")
    public ApiResponse<AiChatResponse> sendMessage(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody AiChatRequest request
    ) {

        if (request == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        AiChatInfo info = aiChatService.sendMessage(request.toCommand(), userId);

        return ApiResponse.success(AiChatResponse.from(info));
    }

    @GetMapping("/messages")
    public ApiResponse<AiChatMessagesResponse> getMessages(
            @AuthenticationPrincipal UUID userId,
            @RequestParam(required = false) Long beforeMessageId,
            @RequestParam(defaultValue = "30") @Min(1) @Max(100) int size
    ) {
        AiChatMessagesInfo info = aiChatService.getMessages(userId, beforeMessageId, size);

        return ApiResponse.success(AiChatMessagesResponse.from(info));
    }
}
