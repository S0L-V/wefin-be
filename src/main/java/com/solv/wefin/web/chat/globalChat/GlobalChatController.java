package com.solv.wefin.web.chat.globalChat;

import com.solv.wefin.domain.chat.globalChat.dto.command.GlobalProfitShareCommand;
import com.solv.wefin.domain.chat.globalChat.service.GlobalChatService;
import com.solv.wefin.global.common.ApiResponse;
import com.solv.wefin.web.chat.globalChat.dto.request.GlobalProfitShareRequest;
import com.solv.wefin.web.chat.globalChat.dto.response.GlobalChatMessageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/chat/global")
public class GlobalChatController {

    private final GlobalChatService globalChatService;

    @GetMapping("/messages")
    public ApiResponse<List<GlobalChatMessageResponse>> getRecentMessage(@RequestParam(defaultValue = "50") int limit) {
        List<GlobalChatMessageResponse> messages = globalChatService.getRecentMessages(limit)
                .stream()
                .map(GlobalChatMessageResponse::from)
                .toList();

        return ApiResponse.success(messages);
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
