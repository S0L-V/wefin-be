package com.solv.wefin.domain.chat.globalChat.service;

import com.solv.wefin.domain.auth.entity.User;
import com.solv.wefin.domain.auth.repository.UserRepository;
import com.solv.wefin.domain.chat.common.constant.ChatScope;
import com.solv.wefin.domain.chat.common.service.ChatSpamGuard;
import com.solv.wefin.domain.chat.globalChat.dto.command.GlobalProfitShareCommand;
import com.solv.wefin.domain.chat.globalChat.entity.ChatRole;
import com.solv.wefin.domain.chat.globalChat.entity.GlobalChatMessage;
import com.solv.wefin.domain.chat.globalChat.event.GlobalChatMessageCreatedEvent;
import com.solv.wefin.domain.chat.globalChat.repository.GlobalChatMessageRepository;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class GlobalChatServiceTest {

    private ApplicationEventPublisher eventPublisher;
    private GlobalChatMessageRepository globalChatMessageRepository;
    private UserRepository userRepository;
    private GlobalChatService globalChatService;
    private ChatSpamGuard chatSpamGuard;

    @BeforeEach
    void setUp() {
        eventPublisher = mock(ApplicationEventPublisher.class);
        globalChatMessageRepository = mock(GlobalChatMessageRepository.class);
        userRepository = mock(UserRepository.class);
        chatSpamGuard = mock(ChatSpamGuard.class);

        globalChatService = new GlobalChatService(
                eventPublisher,
                globalChatMessageRepository,
                userRepository,
                chatSpamGuard
        );
    }

    @Test
    @DisplayName("전체 채팅 사용자 메시지 전송 시 이벤트를 발행한다")
    void sendMessage_success() {
        // given
        UUID userId = UUID.randomUUID();
        String content = "안녕하세요";

        User user = User.builder()
                .email("test1@test.com")
                .nickname("testUser")
                .password("password1")
                .build();
        ReflectionTestUtils.setField(user, "userId", userId);

        GlobalChatMessage savedMessage = GlobalChatMessage.builder()
                .user(user)
                .role(ChatRole.USER)
                .content(content)
                .createdAt(OffsetDateTime.now())
                .build();
        ReflectionTestUtils.setField(savedMessage, "id", 1L);

        when(globalChatMessageRepository.countByUser_UserIdAndCreatedAtAfter(
                eq(userId), any(OffsetDateTime.class))
        ).thenReturn(0L);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(globalChatMessageRepository.save(any(GlobalChatMessage.class))).thenReturn(savedMessage);

        // when
        globalChatService.sendMessage(content, userId);

        // then
        verify(globalChatMessageRepository, times(1))
                .countByUser_UserIdAndCreatedAtAfter(eq(userId), any(OffsetDateTime.class));
        verify(chatSpamGuard, times(1))
                .validate(startsWith(ChatScope.GLOBAL + ":"), eq(0L), any(OffsetDateTime.class));
        verify(userRepository, times(1)).findById(userId);
        verify(globalChatMessageRepository, times(1)).save(any(GlobalChatMessage.class));
        verify(eventPublisher, times(1)).publishEvent(any(GlobalChatMessageCreatedEvent.class));
    }

    @Test
    @DisplayName("메시지가 비어 있으면 예외가 발생한다")
    void sendMessage_fail_blank() {
        // given
        UUID userId = UUID.randomUUID();

        // when // then
        assertThrows(BusinessException.class,
                () -> globalChatService.sendMessage(" ", userId));
    }

    @Test
    @DisplayName("메시지가 1000자를 초과하면 예외가 발생한다")
    void sendMessage_fail_tooLong() {
        // given
        UUID userId = UUID.randomUUID();
        String longMessage = "a".repeat(1001);

        // when // then
        assertThrows(BusinessException.class,
                () -> globalChatService.sendMessage(longMessage, userId));
    }

    @Test
    @DisplayName("존재하지 않는 사용자면 USER_NOT_FOUND 예외가 발생한다")
    void sendMessage_fail_userNotFound() {
        // given
        UUID userId = UUID.randomUUID();
        String content = "안녕하세요";

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // when
        assertThrows(BusinessException.class,
                () -> globalChatService.sendMessage(content, userId));

        // then
        verify(globalChatMessageRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(GlobalChatMessageCreatedEvent.class));
    }

    @Test
    @DisplayName("최근 메시지 조회 시 domain info로 변환한다")
    void getRecentMessages_success() {
        // given
        UUID userId = UUID.randomUUID();

        User user = User.builder()
                .email("test1@test.com")
                .nickname("testUser1")
                .password("password1")
                .build();
        ReflectionTestUtils.setField(user, "userId", userId);

        GlobalChatMessage message = GlobalChatMessage.builder()
                .user(user)
                .role(ChatRole.USER)
                .content("최근 메시지")
                .createdAt(OffsetDateTime.now())
                .build();
        ReflectionTestUtils.setField(message, "id", 10L);

        when(globalChatMessageRepository.findRecentMessages(any()))
                .thenReturn(List.of(message));

        // when
        var result = globalChatService.getRecentMessages(1);

        // then
        assertEquals(1, result.size());
        assertEquals("testUser1", result.get(0).sender());
        assertEquals("USER", result.get(0).role());
    }

    @Test
    @DisplayName("수익 공유 시스템 메시지를 저장하고 이벤트를 발생한다")
    void sendProfitShareMessage_profit_success() {
        // given
        GlobalProfitShareCommand command = GlobalProfitShareCommand.builder()
                .type("PROFIT_ALERT")
                .userNickname("tico")
                .stockName("삼성전자")
                .profitAmount(523000L)
                .build();

        GlobalChatMessage savedMessage = GlobalChatMessage.builder()
                .user(null)
                .role(ChatRole.SYSTEM)
                .content("축하합니다! tico님이 삼성전자에서 523,000원의 수익을 달성하셨습니다!")
                .createdAt(OffsetDateTime.now())
                .build();
        ReflectionTestUtils.setField(savedMessage, "id", 1L);

        when(globalChatMessageRepository.save(any(GlobalChatMessage.class)))
                .thenReturn(savedMessage);

        ArgumentCaptor<GlobalChatMessage> captor = ArgumentCaptor.forClass(GlobalChatMessage.class);

        // when
        globalChatService.sendProfitShareMessage(command);

        // then
        verify(globalChatMessageRepository).save(captor.capture());
        verify(eventPublisher).publishEvent(any(GlobalChatMessageCreatedEvent.class));

        GlobalChatMessage capturedMessage = captor.getValue();
        assertEquals(ChatRole.SYSTEM, capturedMessage.getRole());
        assertEquals("축하합니다! tico님이 삼성전자에서 523,000원의 수익을 달성하셨습니다!", capturedMessage.getContent());
        assertNull(capturedMessage.getUser());
    }


    @Test
    @DisplayName("손익이 0원이면 예외가 발생한다")
    void sendProfitShareMessage_fail_when_profitAmount_zero() {
        // given
        GlobalProfitShareCommand command = GlobalProfitShareCommand.builder()
                .type("PROFIT_ALERT")
                .userNickname("tico")
                .stockName("삼성전자")
                .profitAmount(0L)
                .build();

        // when
        BusinessException exception = assertThrows(BusinessException.class,
                () -> globalChatService.sendProfitShareMessage(command));

        // then
        assertEquals(ErrorCode.INVALID_PROFIT_AMOUNT, exception.getErrorCode());
        verify(globalChatMessageRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }
}
