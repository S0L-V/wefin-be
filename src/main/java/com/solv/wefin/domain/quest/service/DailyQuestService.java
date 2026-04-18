package com.solv.wefin.domain.quest.service;

import com.solv.wefin.domain.quest.entity.DailyQuest;
import com.solv.wefin.domain.quest.entity.QuestTemplate;
import com.solv.wefin.domain.quest.repository.DailyQuestRepository;
import com.solv.wefin.domain.quest.repository.QuestTemplateRepository;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@Transactional
public class DailyQuestService {

    private static final int DAILY_QUEST_COUNT = 3;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final DailyQuestRepository dailyQuestRepository;
    private final QuestTemplateRepository questTemplateRepository;

    public List<DailyQuest> getOrCreateTodayDailyQuests() {
        LocalDate today = LocalDate.now(KST);

        List<DailyQuest> todayQuests = dailyQuestRepository.findAllByQuestDate(today);
        if (!todayQuests.isEmpty()) {
            return todayQuests;
        }

        List<QuestTemplate> templates = new ArrayList<>(questTemplateRepository.findByActiveTrue());
        if (templates.size() < DAILY_QUEST_COUNT) {
            throw new BusinessException(ErrorCode.QUEST_TEMPLATE_NOT_ENOUGH);
        }

        Collections.shuffle(templates);

        List<QuestTemplate> selectedTemplates = templates.subList(0, DAILY_QUEST_COUNT);

        List<DailyQuest> dailyQuests = selectedTemplates.stream()
                .map(template -> DailyQuest.create(
                        template,
                        today,
                        resolveRandomTargetValue(template),
                        template.getReward()
                ))
                .toList();

        try {
            return dailyQuestRepository.saveAllAndFlush(dailyQuests);
        } catch (DataIntegrityViolationException e) {
            return dailyQuestRepository.findAllByQuestDate(today);
        }
    }

    @Transactional(readOnly = true)
    public List<DailyQuest> getTodayDailyQuests() {
        return dailyQuestRepository.findAllByQuestDate(LocalDate.now(KST));
    }

    private int resolveRandomTargetValue(QuestTemplate template) {
        return switch (template.getCode()) {
            case "LOGIN_DAILY" -> 1;
            case "SHARE_NEWS_DAILY" -> randomBetween(1, 3);
            case "USE_AI_CHAT_DAILY" -> randomBetween(1, 5);
            case "SEND_GROUP_CHAT_DAILY" -> randomBetween(2, 5);
            case "BUY_STOCK_DAILY" -> randomBetween(1, 5);
            case "JOIN_GAME_ROOM_DAILY" -> randomBetween(1, 3);
            case "CREATE_GAME_ROOM_DAILY" -> 1;
            default -> template.getTargetValue();
        };
    }

    private int randomBetween(int min, int max) {
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }
}
