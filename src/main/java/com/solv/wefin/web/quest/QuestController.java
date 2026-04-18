package com.solv.wefin.web.quest;

import com.solv.wefin.domain.quest.entity.UserQuest;
import com.solv.wefin.domain.quest.service.UserQuestService;
import com.solv.wefin.global.common.ApiResponse;
import com.solv.wefin.web.quest.dto.response.QuestResponse;
import com.solv.wefin.web.quest.dto.response.TodayQuestListResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/quests")
public class QuestController {

    private final UserQuestService userQuestService;

    @GetMapping("/today")
    public ApiResponse<TodayQuestListResponse> getTodayQuests(
            @AuthenticationPrincipal UUID userId
    ) {
        List<UserQuest> userQuests = userQuestService.getOrIssueTodayUserQuests(userId);

        return ApiResponse.success(TodayQuestListResponse.from(userQuests));
    }

    @PostMapping("/{questId}/reward")
    public ApiResponse<QuestResponse> claimReward(
            @AuthenticationPrincipal UUID userId,
            @PathVariable Long questId
    ) {
        UserQuest userQuest = userQuestService.claimReward(userId, questId);
        return ApiResponse.success(QuestResponse.from(userQuest));
    }
}
