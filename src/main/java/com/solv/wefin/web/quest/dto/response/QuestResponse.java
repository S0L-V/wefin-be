package com.solv.wefin.web.quest.dto.response;

import com.solv.wefin.domain.quest.entity.UserQuest;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

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
        OffsetDateTime expiresAt,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt
) {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    public static QuestResponse from(UserQuest userQuest) {
        LocalDate questDate = userQuest.getDailyQuest().getQuestDate();

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
                questDate,
                questDate.plusDays(1).atStartOfDay(KST).toOffsetDateTime(),
                userQuest.getStartedAt(),
                userQuest.getCompletedAt()
        );
    }
}
