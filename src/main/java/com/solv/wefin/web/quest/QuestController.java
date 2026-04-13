package com.solv.wefin.web.quest;

import com.solv.wefin.domain.quest.entity.UserQuest;
import com.solv.wefin.domain.quest.service.UserQuestService;
import com.solv.wefin.global.common.ApiResponse;
import com.solv.wefin.web.quest.dto.response.TodayQuestListResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
