package com.solv.wefin.domain.quest.service;

import com.solv.wefin.domain.quest.entity.DailyQuest;
import com.solv.wefin.domain.quest.entity.QuestTemplate;
import com.solv.wefin.domain.quest.repository.DailyQuestRepository;
import com.solv.wefin.domain.quest.repository.QuestTemplateRepository;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class DailyQuestServiceTest {

    private DailyQuestRepository dailyQuestRepository;
    private QuestTemplateRepository questTemplateRepository;
    private DailyQuestService dailyQuestService;

    @BeforeEach
    void setUp() {
        dailyQuestRepository = mock(DailyQuestRepository.class);
        questTemplateRepository = mock(QuestTemplateRepository.class);
        dailyQuestService = new DailyQuestService(dailyQuestRepository, questTemplateRepository);
    }

    @Test
    @DisplayName("오늘의 퀘스트가 이미 있으면 기존 퀘스트를 반환한다")
    void getOrCreateTodayDailyQuests_returns_existing() {
        // given
        LocalDate today = LocalDate.now();

        QuestTemplate template = mock(QuestTemplate.class);
        DailyQuest dailyQuest = DailyQuest.create(template, today, 3, 100);
        ReflectionTestUtils.setField(dailyQuest, "id", 1L);

        when(dailyQuestRepository.findAllByQuestDate(today)).thenReturn(List.of(dailyQuest));

        // when
        List<DailyQuest> result = dailyQuestService.getOrCreateTodayDailyQuests();

        // then
        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getId());
        verify(questTemplateRepository, never()).findByActiveTrue();
        verify(dailyQuestRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("오늘의 퀘스트가 없으면 활성 템플릿 3개로 새로 생성한다")
    void getOrCreateTodayDailyQuests_creates_new() {
        // given
        LocalDate today = LocalDate.now();

        QuestTemplate t1 = mockTemplate(1L, 3, 100);
        QuestTemplate t2 = mockTemplate(2L, 2, 80);
        QuestTemplate t3 = mockTemplate(3L, 1, 50);

        when(dailyQuestRepository.findAllByQuestDate(today)).thenReturn(List.of());
        when(questTemplateRepository.findByActiveTrue()).thenReturn(List.of(t1, t2, t3));
        when(dailyQuestRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        List<DailyQuest> result = dailyQuestService.getOrCreateTodayDailyQuests();

        // then
        assertEquals(3, result.size());
        assertTrue(result.stream().allMatch(q -> q.getQuestDate().equals(today)));
        verify(dailyQuestRepository).saveAll(anyList());
    }

    @Test
    @DisplayName("활성 템플릿이 3개보다 적으면 예외가 발생한다")
    void getOrCreateTodayDailyQuests_fail_when_templates_not_enough() {
        // given
        LocalDate today = LocalDate.now();

        QuestTemplate t1 = mockTemplate(1L, 3, 100);
        QuestTemplate t2 = mockTemplate(2L, 2, 80);

        when(dailyQuestRepository.findAllByQuestDate(today)).thenReturn(List.of());
        when(questTemplateRepository.findByActiveTrue()).thenReturn(List.of(t1, t2));

        // when
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> dailyQuestService.getOrCreateTodayDailyQuests()
        );

        // then
        assertEquals(ErrorCode.QUEST_TEMPLATE_NOT_ENOUGH, exception.getErrorCode());
        verify(dailyQuestRepository, never()).saveAll(anyList());
    }

    private QuestTemplate mockTemplate(Long id, Integer targetValue, Integer reward) {
        QuestTemplate template = mock(QuestTemplate.class);
        when(template.getId()).thenReturn(id);
        when(template.getTargetValue()).thenReturn(targetValue);
        when(template.getReward()).thenReturn(reward);
        return template;
    }
}
