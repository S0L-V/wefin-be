package com.solv.wefin.web.chat.globalChat;

import com.solv.wefin.domain.chat.globalChat.service.GlobalChatService;
import com.solv.wefin.global.common.ApiResponse;
import com.solv.wefin.web.chat.globalChat.dto.request.GlobalProfitShareRequest;
import com.solv.wefin.web.chat.globalChat.dto.response.GlobalChatMessageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
    public ApiResponse<Void> sendProfitShareMessage(@RequestBody GlobalProfitShareRequest request) {
        String message;
        String nickname = request.getUserNickname();
        String amount = String.valueOf(request.getProfitAmount());
        String stockName = request.getStockName();

        if (request.getProfitAmount() > 0) {
            message = "축하합니다! " + nickname + "님이 " + stockName + "에서 " + amount + "원의 수익을 달성하셨습니다!";
        } else {
            message = "안타깝네요. " + nickname + "님이 " + stockName + "에서 " + amount + "원을 잃었습니다.";
        }

        globalChatService.sendSystemMessage(message);

        return ApiResponse.success(null);
    }
}
