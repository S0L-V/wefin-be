package com.solv.wefin.web.chat.common;

import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import com.solv.wefin.domain.chat.common.constant.ChatScope;
import com.solv.wefin.domain.chat.common.service.ChatSpamGuard;
import com.solv.wefin.web.chat.common.dto.response.ChatSpamResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;

import java.util.UUID;

@ControllerAdvice
@RequiredArgsConstructor
@ConditionalOnBean(SimpMessagingTemplate.class)
public class ChatWebSocketExceptionHandler {

    private final SimpMessagingTemplate messagingTemplate;
    private final ChatSpamGuard chatSpamGuard;

    @MessageExceptionHandler(BusinessException.class)
    public void handleBusinessException(BusinessException e, SimpMessageHeaderAccessor accessor) {
        if (e.getErrorCode() != ErrorCode.CHAT_SPAM_DETECTED) {
            throw e;
        }

        if (accessor.getSessionAttributes() == null) {
            return;
        }

        Object userIdValue = accessor.getSessionAttributes().get("userId");
        if (!(userIdValue instanceof UUID userId)) {
            return;
        }

        long remainingSeconds = chatSpamGuard.getRemainingSeconds(
                ChatScope.GLOBAL + ":" + userId,
                java.time.OffsetDateTime.now()
        );

        ChatSpamResponse response = ChatSpamResponse.builder()
                .code(e.getErrorCode().name())
                .message(e.getMessage())
                .remainingSeconds(remainingSeconds)
                .build();

        messagingTemplate.convertAndSendToUser(
                userId.toString(),
                "/queue/errors",
                response
        );
    }
}
