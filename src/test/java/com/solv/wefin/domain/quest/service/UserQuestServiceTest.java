package com.solv.wefin.domain.quest.service;

import com.solv.wefin.domain.auth.entity.User;
import com.solv.wefin.domain.auth.repository.UserRepository;
import com.solv.wefin.domain.quest.entity.DailyQuest;
import com.solv.wefin.domain.quest.entity.QuestTemplate;
import com.solv.wefin.domain.quest.entity.UserQuest;
import com.solv.wefin.domain.quest.repository.UserQuestRepository;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class UserQuestServiceTest {

    private UserRepository userRepository;
    private UserQuestRepository userQuestRepository;
    private DailyQuestService dailyQuestService;
    private UserQuestService userQuestService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        userQuestRepository = mock(UserQuestRepository.class);
        dailyQuestService = mock(DailyQuestService.class);
        userQuestService = new UserQuestService(userRepository, userQuestRepository, dailyQuestService);
    }

    @Test
    @DisplayName("오늘 유저 퀘스트가 이미 있으면 기존 퀘스트를 반환한다")
    void getOrIssueTodayUserQuests_returns_existing() {
        // given
        UUID userId = UUID.randomUUID();
        LocalDate today = LocalDate.now();

        UserQuest userQuest = mock(UserQuest.class);

        when(userQuestRepository.findTodayUserQuests(userId, today)).thenReturn(List.of(userQuest));

        // when
        List<UserQuest> result = userQuestService.getOrIssueTodayUserQuests(userId);

        // then
        assertEquals(1, result.size());
        verify(userRepository, never()).findById(any());
        verify(dailyQuestService, never()).getOrCreateTodayDailyQuests();
        verify(userQuestRepository, never()).saveAllAndFlush(anyList());
    }

    @Test
    @DisplayName("오늘 유저 퀘스트가 없으면 오늘의 공통 퀘스트를 발급한다")
    void getOrIssueTodayUserQuests_issues_new() {
        // given
        UUID userId = UUID.randomUUID();
        LocalDate today = LocalDate.now();

        User user = User.builder()
                .email("test@test.com")
                .nickname("questUser")
                .password("password")
                .build();
        ReflectionTestUtils.setField(user, "userId", userId);

        DailyQuest dailyQuest1 = DailyQuest.create(mock(QuestTemplate.class), today, 3, 100);
        DailyQuest dailyQuest2 = DailyQuest.create(mock(QuestTemplate.class), today, 2, 80);
        DailyQuest dailyQuest3 = DailyQuest.create(mock(QuestTemplate.class), today, 1, 50);

        when(userQuestRepository.findTodayUserQuests(userId, today)).thenReturn(List.of());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(dailyQuestService.getOrCreateTodayDailyQuests()).thenReturn(List.of(dailyQuest1, dailyQuest2, dailyQuest3));
        when(userQuestRepository.saveAllAndFlush(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        List<UserQuest> result = userQuestService.getOrIssueTodayUserQuests(userId);

        // then
        assertEquals(3, result.size());
        assertAll(
                () -> assertSame(user, result.get(0).getUser()),
                () -> assertSame(user, result.get(1).getUser()),
                () -> assertSame(user, result.get(2).getUser()),
                () -> assertSame(dailyQuest1, result.get(0).getDailyQuest()),
                () -> assertSame(dailyQuest2, result.get(1).getDailyQuest()),
                () -> assertSame(dailyQuest3, result.get(2).getDailyQuest())
        );
        verify(userQuestRepository).saveAllAndFlush(anyList());
    }

    @Test
    @DisplayName("userId가 null이면 예외가 발생한다")
    void getOrIssueTodayUserQuests_fail_when_user_id_null() {
        // when
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> userQuestService.getOrIssueTodayUserQuests(null)
        );

        // then
        assertEquals(ErrorCode.USER_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    @DisplayName("유저가 없으면 예외가 발생한다")
    void getOrIssueTodayUserQuests_fail_when_user_not_found() {
        // given
        UUID userId = UUID.randomUUID();
        LocalDate today = LocalDate.now();

        when(userQuestRepository.findTodayUserQuests(userId, today)).thenReturn(List.of());
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // when
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> userQuestService.getOrIssueTodayUserQuests(userId)
        );

        // then
        assertEquals(ErrorCode.USER_NOT_FOUND, exception.getErrorCode());
    }
}
