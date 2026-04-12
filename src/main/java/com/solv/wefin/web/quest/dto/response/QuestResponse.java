package com.solv.wefin.web.quest.dto.response;

import com.solv.wefin.domain.quest.entity.UserQuest;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record QuestResponse(
        Long questId,
        Long dailyQuestId,
        Long templateId,
        String code,
        String title,
        String description,
        String status,
        Integer progress,
        Integer targetValue,
        Integer reward,
        LocalDate questDate,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt
) {
    public static QuestResponse from(UserQuest userQuest) {
        return new QuestResponse(
                userQuest.getId(),
                userQuest.getDailyQuest().getId(),
                userQuest.getDailyQuest().getQuestTemplate().getId(),
                userQuest.getDailyQuest().getQuestTemplate().getCode(),
                userQuest.getDailyQuest().getQuestTemplate().getTitle(),
                userQuest.getDailyQuest().getQuestTemplate().getDescription(),
                userQuest.getStatus().name(),
                userQuest.getProgress(),
                userQuest.getDailyQuest().getTargetValue(),
                userQuest.getDailyQuest().getReward(),
                userQuest.getDailyQuest().getQuestDate(),
                userQuest.getStartedAt(),
                userQuest.getCompletedAt()
        );
    }
}
