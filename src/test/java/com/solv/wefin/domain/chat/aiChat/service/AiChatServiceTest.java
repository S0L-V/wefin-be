package com.solv.wefin.domain.chat.aiChat.service;

import com.solv.wefin.domain.auth.entity.User;
import com.solv.wefin.domain.auth.repository.UserRepository;
import com.solv.wefin.domain.chat.aiChat.client.OpenAiChatClient;
import com.solv.wefin.domain.chat.aiChat.dto.command.AiChatCommand;
import com.solv.wefin.domain.chat.aiChat.dto.info.AiChatInfo;
import com.solv.wefin.domain.chat.aiChat.entity.AiChatMessage;
import com.solv.wefin.domain.chat.aiChat.repository.AiChatMessageRepository;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AiChatServiceTest {

    private AiChatMessageRepository aiChatMessageRepository;
    private UserRepository userRepository;
    private OpenAiChatClient openAiChatClient;
    private AiChatService aiChatService;

    @BeforeEach
    void setUp() {
        aiChatMessageRepository = mock(AiChatMessageRepository.class);
        userRepository = mock(UserRepository.class);
        openAiChatClient = mock(OpenAiChatClient.class);

        aiChatService = new AiChatService(
                openAiChatClient,
                aiChatMessageRepository,
                userRepository
        );
    }

    @Test
    @DisplayName("현재 사용자의 AI 채팅 메시지 목록을 반환한다")
    void getMessages_success() {
        // given
        UUID userId = UUID.randomUUID();

        User user = User.builder()
                .email("test@test.com")
                .nickname("ai-user")
                .password("password")
                .build();
        ReflectionTestUtils.setField(user, "userId", userId);

        AiChatMessage firstMessage = AiChatMessage.createUserMessage(user, "삼성전자 어때?");
        ReflectionTestUtils.setField(firstMessage, "messageId", 1L);
        ReflectionTestUtils.setField(firstMessage, "createdAt", OffsetDateTime.now().minusMinutes(1));

        AiChatMessage secondMessage = AiChatMessage.createAiMessage(user, "최근 실적 기준으로 설명드릴게요.");
        ReflectionTestUtils.setField(secondMessage, "messageId", 2L);
        ReflectionTestUtils.setField(secondMessage, "createdAt", OffsetDateTime.now());

        when(aiChatMessageRepository.findByUser_UserIdOrderByCreatedAtAsc(userId))
                .thenReturn(List.of(firstMessage, secondMessage));

        // when
        List<AiChatInfo> result = aiChatService.getMessages(userId);

        // then
        assertEquals(2, result.size());
        assertEquals("USER", result.get(0).role());
        assertEquals("삼성전자 어때?", result.get(0).content());
        assertEquals("AI", result.get(1).role());
        assertEquals("최근 실적 기준으로 설명드릴게요.", result.get(1).content());
    }

    @Test
    @DisplayName("AI 채팅 요청 시 사용자 메시지와 AI 메시지를 저장하고 AI 답변을 반환한다")
    void sendMessage_success() {
        // given
        UUID userId = UUID.randomUUID();
        AiChatCommand command = new AiChatCommand("삼성전자 전망 알려줘");

        User user = User.builder()
                .email("test@test.com")
                .nickname("ai-user")
                .password("password")
                .build();
        ReflectionTestUtils.setField(user, "userId", userId);

        AiChatMessage historyUserMessage = AiChatMessage.createUserMessage(user, "삼성전자 전망 알려줘");
        ReflectionTestUtils.setField(historyUserMessage, "messageId", 1L);
        ReflectionTestUtils.setField(historyUserMessage, "createdAt", OffsetDateTime.now().minusSeconds(10));

        AiChatMessage savedAiMessage = AiChatMessage.createAiMessage(user, "최근 실적 기준으로 설명드릴게요.");
        ReflectionTestUtils.setField(savedAiMessage, "messageId", 2L);
        ReflectionTestUtils.setField(savedAiMessage, "createdAt", OffsetDateTime.now());

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(aiChatMessageRepository.findTop10ByUser_UserIdOrderByCreatedAtDesc(userId))
                .thenReturn(List.of(historyUserMessage));
        when(openAiChatClient.ask(any()))
                .thenReturn("최근 실적 기준으로 설명드릴게요.");
        when(aiChatMessageRepository.save(any(AiChatMessage.class)))
                .thenReturn(historyUserMessage)
                .thenReturn(savedAiMessage);

        ArgumentCaptor<AiChatMessage> messageCaptor = ArgumentCaptor.forClass(AiChatMessage.class);

        // when
        AiChatInfo result = aiChatService.sendMessage(command, userId);

        // then
        verify(aiChatMessageRepository, times(2)).save(messageCaptor.capture());
        verify(openAiChatClient, times(1)).ask(any());

        List<AiChatMessage> savedMessages = messageCaptor.getAllValues();
        assertEquals("USER", savedMessages.get(0).getRole().name());
        assertEquals("삼성전자 전망 알려줘", savedMessages.get(0).getContent());
        assertEquals("AI", savedMessages.get(1).getRole().name());
        assertEquals("최근 실적 기준으로 설명드릴게요.", savedMessages.get(1).getContent());

        assertEquals(2L, result.messageId());
        assertEquals(userId, result.userId());
        assertEquals("AI", result.role());
        assertEquals("최근 실적 기준으로 설명드릴게요.", result.content());
    }

    @Test
    @DisplayName("입력 메시지가 비어 있으면 예외가 발생한다")
    void sendMessage_fail_blank() {
        // given
        UUID userId = UUID.randomUUID();
        AiChatCommand command = new AiChatCommand(" ");

        // when
        BusinessException exception = assertThrows(BusinessException.class,
                () -> aiChatService.sendMessage(command, userId));

        // then
        assertEquals(ErrorCode.CHAT_MESSAGE_EMPTY, exception.getErrorCode());
    }

    @Test
    @DisplayName("입력 메시지가 최대 길이를 초과하면 예외가 발생한다")
    void sendMessage_fail_too_long() {
        // given
        UUID userId = UUID.randomUUID();
        String longMessage = "a".repeat(1001);
        AiChatCommand command = new AiChatCommand(longMessage);

        // when
        BusinessException exception = assertThrows(BusinessException.class,
                () -> aiChatService.sendMessage(command, userId));

        // then
        assertEquals(ErrorCode.CHAT_MESSAGE_TOO_LONG, exception.getErrorCode());
    }

    @Test
    @DisplayName("command가 null이면 예외가 발생한다")
    void sendMessage_fail_null_command() {
        // given
        UUID userId = UUID.randomUUID();

        // when
        BusinessException exception = assertThrows(BusinessException.class,
                () -> aiChatService.sendMessage(null, userId));

        // then
        assertEquals(ErrorCode.INVALID_INPUT, exception.getErrorCode());
    }

    @Test
    @DisplayName("userId가 null이면 예외가 발생한다")
    void sendMessage_fail_null_userId() {
        // given
        AiChatCommand command = new AiChatCommand("질문");

        // when
        BusinessException exception = assertThrows(BusinessException.class,
                () -> aiChatService.sendMessage(command, null));

        // then
        assertEquals(ErrorCode.USER_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    @DisplayName("사용자를 찾을 수 없으면 예외가 발생한다")
    void sendMessage_fail_user_not_found() {
        // given
        UUID userId = UUID.randomUUID();
        AiChatCommand command = new AiChatCommand("질문");

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // when
        BusinessException exception = assertThrows(BusinessException.class,
                () -> aiChatService.sendMessage(command, userId));

        // then
        assertEquals(ErrorCode.USER_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    @DisplayName("AI 클라이언트 호출 실패 시 예외를 전파한다")
    void sendMessage_fail_when_client_throws() {
        // given
        UUID userId = UUID.randomUUID();
        AiChatCommand command = new AiChatCommand("질문");

        User user = User.builder()
                .email("test@test.com")
                .nickname("ai-user")
                .password("password")
                .build();
        ReflectionTestUtils.setField(user, "userId", userId);

        AiChatMessage historyUserMessage = AiChatMessage.createUserMessage(user, "질문");
        ReflectionTestUtils.setField(historyUserMessage, "messageId", 1L);
        ReflectionTestUtils.setField(historyUserMessage, "createdAt", OffsetDateTime.now());

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(aiChatMessageRepository.save(any(AiChatMessage.class))).thenReturn(historyUserMessage);
        when(aiChatMessageRepository.findTop10ByUser_UserIdOrderByCreatedAtDesc(userId))
                .thenReturn(List.of(historyUserMessage));
        when(openAiChatClient.ask(any()))
                .thenThrow(new BusinessException(ErrorCode.AI_CHAT_REQUEST_FAILED));

        // when
        BusinessException exception = assertThrows(BusinessException.class,
                () -> aiChatService.sendMessage(command, userId));

        // then
        assertEquals(ErrorCode.AI_CHAT_REQUEST_FAILED, exception.getErrorCode());
    }

    @Test
    @DisplayName("AI 응답 시간이 초과되면 예외를 전파한다")
    void sendMessage_fail_timeout() {
        // given
        UUID userId = UUID.randomUUID();
        AiChatCommand command = new AiChatCommand("질문");

        User user = User.builder()
                .email("test@test.com")
                .nickname("ai-user")
                .password("password")
                .build();
        ReflectionTestUtils.setField(user, "userId", userId);

        AiChatMessage historyUserMessage = AiChatMessage.createUserMessage(user, "질문");
        ReflectionTestUtils.setField(historyUserMessage, "messageId", 1L);
        ReflectionTestUtils.setField(historyUserMessage, "createdAt", OffsetDateTime.now());

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(aiChatMessageRepository.save(any(AiChatMessage.class))).thenReturn(historyUserMessage);
        when(aiChatMessageRepository.findTop10ByUser_UserIdOrderByCreatedAtDesc(userId))
                .thenReturn(List.of(historyUserMessage));
        when(openAiChatClient.ask(any()))
                .thenThrow(new BusinessException(ErrorCode.AI_CHAT_TIMEOUT));

        // when
        BusinessException exception = assertThrows(BusinessException.class,
                () -> aiChatService.sendMessage(command, userId));

        // then
        assertEquals(ErrorCode.AI_CHAT_TIMEOUT, exception.getErrorCode());
    }
}
