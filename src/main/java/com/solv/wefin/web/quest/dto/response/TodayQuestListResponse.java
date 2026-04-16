package com.solv.wefin.web.quest.dto.response;

import com.solv.wefin.domain.quest.entity.UserQuest;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

public record TodayQuestListResponse(
        LocalDate questDate,
        OffsetDateTime expiresAt,
        List<QuestResponse> quests
) {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    public static TodayQuestListResponse from(List<UserQuest> userQuests) {
        LocalDate questDate = userQuests.isEmpty()
                ? LocalDate.now(KST)
                : userQuests.get(0).getDailyQuest().getQuestDate();

        return new TodayQuestListResponse(
                questDate,
                questDate.plusDays(1).atStartOfDay(KST).toOffsetDateTime(),
                userQuests.stream()
                        .map(QuestResponse::from)
                        .toList()
        );
    }
}
