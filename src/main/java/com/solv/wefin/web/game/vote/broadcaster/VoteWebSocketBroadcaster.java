package com.solv.wefin.web.game.vote.broadcaster;

import com.solv.wefin.domain.auth.repository.UserRepository;
import com.solv.wefin.domain.game.vote.VoteBroadcaster;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class VoteWebSocketBroadcaster implements VoteBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;
    private final UserRepository userRepository;

    @Override
    public void broadcastStart(UUID roomId, UUID initiatorId, int totalCount, int timeoutSeconds) {
        String nickname = userRepository.findById(initiatorId)
                .map(user -> user.getNickname())
                .orElse("알 수 없음");

        Map<String, Object> payload = Map.of(
                "type", "VOTE_START",
                "initiator", nickname,
                "totalCount", totalCount,
                "timeoutSeconds", timeoutSeconds
        );

        send(roomId, payload);
        log.info("[투표 WS] VOTE_START: roomId={}, initiator={}, totalCount={}", roomId, nickname, totalCount);
    }

    @Override
    public void broadcastUpdate(UUID roomId, long agreeCount, long disagreeCount, int totalCount) {
        Map<String, Object> payload = Map.of(
                "type", "VOTE_UPDATE",
                "agreeCount", agreeCount,
                "disagreeCount", disagreeCount,
                "totalCount", totalCount
        );

        send(roomId, payload);
    }

    @Override
    public void broadcastResult(UUID roomId, boolean passed, long agreeCount, long disagreeCount) {
        Map<String, Object> payload = Map.of(
                "type", "VOTE_RESULT",
                "passed", passed,
                "agreeCount", agreeCount,
                "disagreeCount", disagreeCount
        );

        send(roomId, payload);
        log.info("[투표 WS] VOTE_RESULT: roomId={}, passed={}, 찬성={}, 반대={}",
                roomId, passed, agreeCount, disagreeCount);
    }

    private void send(UUID roomId, Map<String, Object> payload) {
        try {
            messagingTemplate.convertAndSend("/topic/rooms/" + roomId + "/vote", payload);
        } catch (Exception e) {
            log.error("[투표 WS] 전송 실패: roomId={}, type={}, error={}",
                    roomId, payload.get("type"), e.getMessage(), e);
        }
    }
}
