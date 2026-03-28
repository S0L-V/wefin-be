package com.solv.wefin.global.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

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
            String nickname = accessor.getFirstNativeHeader("nickname");

            // jwt 토큰 검증
//            String token = accessor.getFirstNativeHeader("Authorization");
            if (nickname == null || nickname.isBlank()) {
                nickname = "anonymous";
            }

            accessor.setUser(
                    new UsernamePasswordAuthenticationToken(nickname, null)
            );

            log.info("WebSocket CONNECT nickname={}", nickname);
        }

        // SUBSCRIBE 일때 구독 destination 로그 출력
        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            log.info("WebSocket SUBSCRIBE destination={}", accessor.getDestination());
        }

        // SEnD 일때 전송 destination 로그 출력
        if (StompCommand.SEND.equals(accessor.getCommand())) {
            log.info("WebSocket SEND destination={}", accessor.getDestination());
        }

        return message;


    }
}
