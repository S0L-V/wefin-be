package com.solv.wefin.domain.chat.common.service;

import com.solv.wefin.domain.chat.common.exception.ChatSpamException;
import com.solv.wefin.global.error.ErrorCode;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ChatSpamGuard {

    private static final long BLOCK_SECONDS = 10L;

    // 차단된 사용자 저장 (key = scope:userId)
    private final Map<String, OffsetDateTime> blockedUsers = new ConcurrentHashMap<>();

    /**
     * @param blockKey      채팅 범위 + 사용자 (ex : GLOBAL:uuid)
     * @param recentCount   최근 n초 메시지 개수 (DB 조회값)
     * @param now           현재 시간
     */
    public void validate(String blockKey, long recentCount, OffsetDateTime now) {

        // 1. 이미 차단된 사용자 체크
        OffsetDateTime blockedUntil = blockedUsers.get(blockKey);

        if (blockedUntil != null) {
            if(now.isBefore(blockedUntil)) {
                // 아직 차단 시간 안지났으면 바로 차단
                long remainingSeconds = getRemainingSeconds(blockKey, now);
                throw new ChatSpamException(
                        ErrorCode.CHAT_SPAM_DETECTED,
                        "도배가 감지되었습니다. " + remainingSeconds + "초 후에 다시 시도해주세요.",
                        blockKey
                );
            }

            // 차단 시간 지났으면 해제
            blockedUsers.remove(blockKey, blockedUntil  );

        }

        // 최근 메시지 개수 기준 차단
        if(recentCount >= 5) {
            blockedUsers.put(blockKey, now.plusSeconds(BLOCK_SECONDS));
            throw new ChatSpamException(
                    ErrorCode.CHAT_SPAM_DETECTED,
                    "도배가 감지되었습니다. " + BLOCK_SECONDS + "초 후에 다시 시도해주세요.",
                    blockKey
            );
        }

    }

    // 남은 차단 시간(초) 반환
    public long getRemainingSeconds(String blockKey, OffsetDateTime now) {

        OffsetDateTime blockedUntil = blockedUsers.get(blockKey);

        if (blockedUntil == null) {
            return 0L;
        }

        if (!now.isBefore(blockedUntil)) {
            blockedUsers.remove(blockKey, blockedUntil);
            return 0L;
        }

        return Math.max(1, Duration.between(now, blockedUntil).getSeconds());
    }
}
