package com.solv.wefin.web.game.turn.listener;

import com.solv.wefin.domain.auth.entity.User;
import com.solv.wefin.domain.auth.repository.UserRepository;
import com.solv.wefin.domain.game.turn.event.TurnChangeEvent;
import com.solv.wefin.web.game.turn.dto.response.TurnChangeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT;

@Slf4j
@Component
@RequiredArgsConstructor
public class TurnChangeEventListener {

    private final SimpMessagingTemplate messagingTemplate;
    private final UserRepository userRepository;

    @TransactionalEventListener(phase = AFTER_COMMIT)
    public void handle(TurnChangeEvent event) {
        // SnapshotData에서 userId 추출 → User 테이블에서 닉네임 일괄 조회
        List<UUID> userIds = event.snapshots().stream()
                .map(TurnChangeEvent.SnapshotData::userId)
                .toList();

        Map<UUID, String> nicknameMap = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getUserId, User::getNickname));

        TurnChangeMessage message = TurnChangeMessage.from(
                event.turnNumber(), event.turnDate(),
                event.briefingId(), event.snapshots(), nicknameMap);

        String destination = "/topic/rooms/" + event.roomId() + "/turn";
        log.info("[턴 전환 WS] roomId={}, turn={}, rankings={}명",
                event.roomId(), event.turnNumber(), message.rankings().size());

        messagingTemplate.convertAndSend(destination, message);
    }
}
