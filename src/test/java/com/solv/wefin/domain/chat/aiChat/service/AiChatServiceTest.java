package com.solv.wefin.domain.chat.aiChat.service;

import com.solv.wefin.domain.auth.entity.User;
import com.solv.wefin.domain.auth.repository.UserRepository;
import com.solv.wefin.domain.chat.aiChat.client.OpenAiChatClient;
import com.solv.wefin.domain.chat.aiChat.dto.command.AiChatCommand;
import com.solv.wefin.domain.chat.aiChat.dto.info.AiChatInfo;
import com.solv.wefin.domain.chat.aiChat.entity.AiChatMessage;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiChatServiceTest {

    private AiChatMessagePersistenceService aiChatMessagePersistenceService;
    private UserRepository userRepository;
    private OpenAiChatClient openAiChatClient;
    private AiChatService aiChatService;

    @BeforeEach
    void setUp() {
        aiChatMessagePersistenceService = mock(AiChatMessagePersistenceService.class);
        userRepository = mock(UserRepository.class);
        openAiChatClient = mock(OpenAiChatClient.class);

        aiChatService = new AiChatService(
                openAiChatClient,
                aiChatMessagePersistenceService,
                userRepository
        );
    }

    @Test
    @DisplayName("현재 사용자의 AI 채팅 메시지 목록을 반환한다")
    void getMessages_success() {
        // given
        UUID userId = UUID.randomUUID();
        User user = createUser(userId);

        AiChatMessage firstMessage = AiChatMessage.createUserMessage(user, "삼성전자 어때?");
        ReflectionTestUtils.setField(firstMessage, "messageId", 1L);
        ReflectionTestUtils.setField(firstMessage, "createdAt", OffsetDateTime.now().minusMinutes(1));

        AiChatMessage secondMessage = AiChatMessage.createAiMessage(user, "최근 실적 기준으로 설명드릴게요.");
        ReflectionTestUtils.setField(secondMessage, "messageId", 2L);
        ReflectionTestUtils.setField(secondMessage, "createdAt", OffsetDateTime.now());

        when(aiChatMessagePersistenceService.getMessages(userId))
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
    @DisplayName("과거 history와 현재 질문을 분리해 AI에 전달하고 사용자와 AI 메시지를 저장한다")
    void sendMessage_success() {
        // given
        UUID userId = UUID.randomUUID();
        AiChatCommand command = new AiChatCommand("삼성전자 전망 알려줘");
        User user = createUser(userId);

        AiChatMessage historyUserMessage = AiChatMessage.createUserMessage(user, "이전에 나눈 질문");
        ReflectionTestUtils.setField(historyUserMessage, "messageId", 1L);
        ReflectionTestUtils.setField(historyUserMessage, "createdAt", OffsetDateTime.now().minusSeconds(10));

        AiChatMessage savedUserMessage = AiChatMessage.createUserMessage(user, command.message());
        ReflectionTestUtils.setField(savedUserMessage, "messageId", 2L);
        ReflectionTestUtils.setField(savedUserMessage, "createdAt", OffsetDateTime.now().minusSeconds(5));

        AiChatMessage savedAiMessage = AiChatMessage.createAiMessage(user, "최근 실적 기준으로 설명드릴게요.");
        ReflectionTestUtils.setField(savedAiMessage, "messageId", 3L);
        ReflectionTestUtils.setField(savedAiMessage, "createdAt", OffsetDateTime.now());

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(aiChatMessagePersistenceService.getRecentHistory(userId))
                .thenReturn(List.of(historyUserMessage));
        when(openAiChatClient.ask(anyList(), eq(command.message())))
                .thenReturn("최근 실적 기준으로 설명드릴게요.");
        when(aiChatMessagePersistenceService.saveUserMessage(user, command.message()))
                .thenReturn(savedUserMessage);
        when(aiChatMessagePersistenceService.saveAiMessage(user, "최근 실적 기준으로 설명드릴게요."))
                .thenReturn(savedAiMessage);

        ArgumentCaptor<List<AiChatMessage>> historyCaptor = ArgumentCaptor.forClass(List.class);

        // when
        AiChatInfo result = aiChatService.sendMessage(command, userId);

        // then
        verify(aiChatMessagePersistenceService).saveUserMessage(user, command.message());
        verify(aiChatMessagePersistenceService).saveAiMessage(user, "최근 실적 기준으로 설명드릴게요.");
        verify(openAiChatClient).ask(historyCaptor.capture(), eq(command.message()));

        List<AiChatMessage> history = historyCaptor.getValue();
        assertEquals(1, history.size());
        assertEquals("이전에 나눈 질문", history.get(0).getContent());

        assertEquals(3L, result.messageId());
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
        AiChatCommand command = new AiChatCommand("a".repeat(1001));

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
        User user = createUser(userId);

        AiChatMessage historyUserMessage = AiChatMessage.createUserMessage(user, "이전 질문");
        ReflectionTestUtils.setField(historyUserMessage, "messageId", 1L);
        ReflectionTestUtils.setField(historyUserMessage, "createdAt", OffsetDateTime.now());

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(aiChatMessagePersistenceService.getRecentHistory(userId))
                .thenReturn(List.of(historyUserMessage));
        when(openAiChatClient.ask(anyList(), eq(command.message())))
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
        User user = createUser(userId);

        AiChatMessage historyUserMessage = AiChatMessage.createUserMessage(user, "이전 질문");
        ReflectionTestUtils.setField(historyUserMessage, "messageId", 1L);
        ReflectionTestUtils.setField(historyUserMessage, "createdAt", OffsetDateTime.now());

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(aiChatMessagePersistenceService.getRecentHistory(userId))
                .thenReturn(List.of(historyUserMessage));
        when(openAiChatClient.ask(anyList(), eq(command.message())))
                .thenThrow(new BusinessException(ErrorCode.AI_CHAT_TIMEOUT));

        // when
        BusinessException exception = assertThrows(BusinessException.class,
                () -> aiChatService.sendMessage(command, userId));

        // then
        assertEquals(ErrorCode.AI_CHAT_TIMEOUT, exception.getErrorCode());
    }

    private User createUser(UUID userId) {
        User user = User.builder()
                .email("test@test.com")
                .nickname("ai-user")
                .password("password")
                .build();
        ReflectionTestUtils.setField(user, "userId", userId);
        return user;
    }
}