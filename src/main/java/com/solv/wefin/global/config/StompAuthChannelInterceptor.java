package com.solv.wefin.global.config;

import com.solv.wefin.domain.auth.repository.UserRepository;
import com.solv.wefin.global.config.security.JwtProvider;
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

import java.security.Principal;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final Set<String> ANONYMOUS_SUBSCRIBE_DESTINATIONS = Set.of(
            "/topic/chat/global"
    );

    private final JwtProvider jwtProvider;
    private final UserRepository userRepository;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null) {
            return message;
        }

        // CONNECT 일때 id를 웹소켓 사용자로 저장
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            handleConnect(accessor);
        }

        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            handleSubscribe(accessor);
        }

        if (StompCommand.SEND.equals(accessor.getCommand())) {
            handleSend(accessor);
        }

        return message;
    }

    private void handleConnect(StompHeaderAccessor accessor) {
        String authorizationHeader = accessor.getFirstNativeHeader("Authorization");

        // jwt 토큰 검증
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            log.info("WebSocket CONNECT anonymous");
            return;
        }

        if (!authorizationHeader.startsWith(BEARER_PREFIX)) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
        }

        String token = authorizationHeader.substring(BEARER_PREFIX.length());

        boolean validAccessToken;
        try {
            validAccessToken = jwtProvider.isValid(token) && jwtProvider.isAccessToken(token);
        } catch (RuntimeException e) {
            validAccessToken = false;
        }

        if (!validAccessToken) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN);
        }

        UUID userId;
        try {
            userId = jwtProvider.getUserId(token);
        } catch (RuntimeException e) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN);
        }

        if (!userRepository.existsById(userId)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        if (sessionAttributes == null) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }

        sessionAttributes.put("userId", userId);
        accessor.setUser(new StompPrincipal(userId.toString()));

        log.info("WebSocket CONNECT user={}", userId);
    }

    // SUBSCRIBE 일때 구독 destination 로그 출력
    private void handleSubscribe(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();

        if (!isAuthenticated(accessor) && !ANONYMOUS_SUBSCRIBE_DESTINATIONS.contains(destination)) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
        }

        log.info("WebSocket SUBSCRIBE destination={}", destination);
    }

    // SEND 일때 전송 destination 로그 출력
    private void handleSend(StompHeaderAccessor accessor) {
        if (!isAuthenticated(accessor)) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
        }

        log.info("WebSocket SEND destination={}", accessor.getDestination());
    }

    private boolean isAuthenticated(StompHeaderAccessor accessor) {
        Principal user = accessor.getUser();
        if (user != null && user.getName() != null && !user.getName().isBlank()) {
            return true;
        }

        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        return sessionAttributes != null && sessionAttributes.get("userId") instanceof UUID;
    }

    private record StompPrincipal(String value) implements Principal {
        @Override
        public String getName() {
            return value;
        }
    }
}
