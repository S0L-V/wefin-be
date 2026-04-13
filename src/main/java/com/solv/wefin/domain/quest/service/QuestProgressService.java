package com.solv.wefin.domain.quest.service;

import com.solv.wefin.domain.quest.entity.QuestCompleteType;
import com.solv.wefin.domain.quest.entity.QuestEventType;
import com.solv.wefin.domain.quest.entity.UserQuest;
import com.solv.wefin.domain.quest.repository.UserQuestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class QuestProgressService {

    private final UserQuestRepository userQuestRepository;

    public void handleEvent(UUID userId, QuestEventType eventType) {
        List<UserQuest> todayUserQuests =
                userQuestRepository.findTodayUserQuests(userId, LocalDate.now());

        todayUserQuests.stream()
                .filter(userQuest -> userQuest.getDailyQuest()
                        .getQuestTemplate()
                        .getEventType() == eventType)
                .forEach(userQuest ->
                        userQuest.updateProgress(userQuest.getProgress() + 1)
                );
    }

    public void handleProfitRate(UUID userId, BigDecimal profitRate) {
        List<UserQuest> todayUserQuests =
                userQuestRepository.findTodayUserQuests(userId, LocalDate.now());

        todayUserQuests.stream()
                .filter(userQuest -> userQuest.getDailyQuest()
                        .getQuestTemplate()
                        .getEventType() == QuestEventType.CHECK_PROFIT_RATE)
                .filter(userQuest -> userQuest.getDailyQuest()
                        .getQuestTemplate()
                        .getCompleteType() == QuestCompleteType.PERCENT)
                .forEach(userQuest -> {
                    int currentRate = profitRate.intValue();
                    userQuest.updateProgress(currentRate);
                });
    }

    public void handleGameRank(UUID userId, int rank) {
        List<UserQuest> todayUserQuests =
                userQuestRepository.findTodayUserQuests(userId, LocalDate.now());

        todayUserQuests.stream()
                .filter(userQuest -> userQuest.getDailyQuest()
                        .getQuestTemplate()
                        .getEventType() == QuestEventType.CHECK_GAME_RANK)
                .forEach(userQuest -> {
                    Integer targetRank = userQuest.getDailyQuest().getTargetValue();

                    if (targetRank != null && rank <= targetRank) {
                        userQuest.completeWithProgress(rank);
                        return;
                    }

                    userQuest.recordProgress(rank);
                });
    }
}
