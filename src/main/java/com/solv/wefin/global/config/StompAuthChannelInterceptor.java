package com.solv.wefin.global.config;

import com.solv.wefin.domain.auth.repository.UserRepository;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private final UserRepository userRepository;

    // 메시지가 채널로 보내지기 직전 호출 -> 조건에 안맞으면 차단(지금은 안함), 사용자 정보 넣기
    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if(accessor == null) {
            return message;
        }

        // CONNECT 일때 nickname을 웹소켓 사용자로 저장
        if(StompCommand.CONNECT.equals(accessor.getCommand())) {
            String userIdHeader = accessor.getFirstNativeHeader("userId");

            // jwt 토큰 검증
//            String token = accessor.getFirstNativeHeader("Authorization");
            if (userIdHeader == null || userIdHeader.isBlank()) {
                throw new BusinessException(ErrorCode.USER_NOT_FOUND);
            }

            UUID userId;
            try {
                userId = UUID.fromString(userIdHeader);
            } catch (IllegalArgumentException e) {
                throw new BusinessException(ErrorCode.INVALID_INPUT);
            }

            boolean exists = userRepository.existsById(userId);

            if (!exists) {
                throw new BusinessException(ErrorCode.USER_NOT_FOUND);
            }

            Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
            if (sessionAttributes == null) {
                throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
            }

            accessor.getSessionAttributes().put("userId", userId);

            log.info("CONNECT userIdHeader={}", userIdHeader);
            log.info("existsById={}", exists);
            log.info("WebSocket CONNECT user={}", userId);
        }

        // SUBSCRIBE 일때 구독 destination 로그 출력
        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            log.info("WebSocket SUBSCRIBE destination={}", accessor.getDestination());
        }

        // SEND 일때 전송 destination 로그 출력
        if (StompCommand.SEND.equals(accessor.getCommand())) {
            log.info("WebSocket SEND destination={}", accessor.getDestination());
        }

        return message;


    }
}
