package com.solv.wefin.web.chat.common;

import com.solv.wefin.domain.chat.common.exception.ChatSpamException;
import com.solv.wefin.domain.chat.common.service.ChatSpamGuard;
import com.solv.wefin.web.chat.common.dto.response.ChatSpamResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.ControllerAdvice;

import java.time.OffsetDateTime;
import java.util.UUID;

/*
 * 전역 예외 처리 빈으로 등록한다.
 * HTTP ControllerAdvice처럼 동작하지만, 여기서는 STOMP 메시지 처리 중 발생한 예외도 잡을 수 있다.
 */
@ControllerAdvice
@RequiredArgsConstructor

/*
 * SimpMessagingTemplate 빈이 있을 때만 이 클래스를 Spring 빈으로 등록
 * 이유:
 * - 이 클래스는 WebSocket/STOMP 환경에서만 필요
 * - WebSocket 메시징 환경일 때만 로드되게 조건을 걸음
 */
@ConditionalOnBean(SimpMessagingTemplate.class)
public class ChatWebSocketExceptionHandler {

    private final SimpMessagingTemplate messagingTemplate;
    private final ChatSpamGuard chatSpamGuard;

    /*
     * STOMP 메시지 처리 중 ChatSpamException 발생했을 때 이 메서드가 호출된다.
     *
     * @MessageExceptionHandler는 HTTP의 @ExceptionHandler와 비슷한 역할인데,
     * WebSocket/STOMP 메시지 처리용 예외를 잡는 어노테이션이다.
     */
    @MessageExceptionHandler(ChatSpamException.class)
    public void handleChatSpamException(ChatSpamException e, SimpMessageHeaderAccessor accessor) {

        if (accessor.getSessionAttributes() == null) {
            return;
        }

        Object userIdValue = accessor.getSessionAttributes().get("userId");
        if (!(userIdValue instanceof UUID userId)) {
            return;
        }

        long remainingSeconds = chatSpamGuard.getRemainingSeconds(
                e.getBlockKey(),
                OffsetDateTime.now()
        );

        ChatSpamResponse response = ChatSpamResponse.builder()
                .code(e.getErrorCode().name())
                .message(e.getMessage())
                .remainingSeconds(remainingSeconds)
                .build();

        /*
         * 특정 사용자에게만 에러 메시지를 전송한다.
         * userId.toString():
         * - STOMP Principal 이름과 맞춰서 user destination 라우팅 키로 사용
         * - Spring이 user destination prefix("/user")를 붙여서 해당 사용자 큐로 라우팅해 준다.
         */
        messagingTemplate.convertAndSendToUser(
                userId.toString(),
                "/queue/errors",
                response
        );
    }
}
