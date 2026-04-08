package com.solv.wefin.web.chat.globalChat;

import com.solv.wefin.domain.chat.globalChat.dto.command.GlobalProfitShareCommand;
import com.solv.wefin.domain.chat.globalChat.dto.info.GlobalChatMessagesInfo;
import com.solv.wefin.domain.chat.globalChat.service.GlobalChatService;
import com.solv.wefin.global.common.ApiResponse;
import com.solv.wefin.web.chat.globalChat.dto.request.GlobalProfitShareRequest;
import com.solv.wefin.web.chat.globalChat.dto.response.GlobalChatMessagesResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;


@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/chat/global")
public class GlobalChatController {

    private final GlobalChatService globalChatService;

    @GetMapping("/messages")
    public ApiResponse<GlobalChatMessagesResponse> getRecentMessage(
            @RequestParam(required = false) Long beforeMessageId,
            @RequestParam(defaultValue = "30") @Min(1) @Max(100) int size
    ) {
        GlobalChatMessagesInfo info = globalChatService.getMessages(beforeMessageId, size);

        return ApiResponse.success(GlobalChatMessagesResponse.from(info));
    }

    @PostMapping("/profit-share")
    public ApiResponse<Void> sendProfitShareMessage(@Valid @RequestBody GlobalProfitShareRequest request) {
        GlobalProfitShareCommand command = GlobalProfitShareCommand.builder()
                .type(request.getType())
                .userNickname(request.getUserNickname())
                .stockName(request.getStockName())
                .profitAmount(request.getProfitAmount())
                .build();

        globalChatService.sendProfitShareMessage(command);

        return ApiResponse.success(null);
    }
}
