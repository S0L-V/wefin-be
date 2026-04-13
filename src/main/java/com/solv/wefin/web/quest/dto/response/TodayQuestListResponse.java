package com.solv.wefin.web.quest.dto.response;

import com.solv.wefin.domain.quest.entity.UserQuest;

import java.time.LocalDate;
import java.util.List;

public record TodayQuestListResponse(
        LocalDate questDate,
        List<QuestResponse> quests
) {
    public static TodayQuestListResponse from(List<UserQuest> userQuests) {
        LocalDate questDate = userQuests.isEmpty()
                ? LocalDate.now()
                : userQuests.get(0).getDailyQuest().getQuestDate();

        return new TodayQuestListResponse(
                questDate,
                userQuests.stream()
                        .map(QuestResponse::from)
                        .toList()
        );
    }
}
