package com.solv.wefin.global.config;

import com.solv.wefin.domain.auth.repository.UserRepository;
import com.solv.wefin.global.config.security.JwtProvider;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class StompAuthChannelInterceptorTest {

    @Mock
    private JwtProvider jwtProvider;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private StompAuthChannelInterceptor interceptor;

    @Test
    @DisplayName("익명 사용자는 전체 채팅 토픽 구독을 할 수 있다")
    void anonymousSubscribe_globalTopic_success() {
        Message<byte[]> message = createMessage(StompCommand.SUBSCRIBE, "/topic/chat/global");

        assertDoesNotThrow(() -> interceptor.preSend(message, null));
    }

    @Test
    @DisplayName("익명 사용자는 그룹 채팅 토픽을 구독할 수 없다")
    void anonymousSubscribe_groupTopic_fail() {
        Message<byte[]> message = createMessage(StompCommand.SUBSCRIBE, "/topic/chat/group/1");

        BusinessException exception =
                assertThrows(BusinessException.class, () -> interceptor.preSend(message, null));

        assertEquals(ErrorCode.AUTH_UNAUTHORIZED, exception.getErrorCode());
    }

    @Test
    @DisplayName("익명 사용자는 전체 채팅 메시지를 전송할 수 없다")
    void anonymousSend_globalChat_fail() {
        Message<byte[]> message = createMessage(StompCommand.SEND, "/app/chat/global/send");

        BusinessException exception =
                assertThrows(BusinessException.class, () -> interceptor.preSend(message, null));

        assertEquals(ErrorCode.AUTH_UNAUTHORIZED, exception.getErrorCode());
    }

    private Message<byte[]> createMessage(StompCommand command, String destination) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setLeaveMutable(true);
        accessor.setDestination(destination);

        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
