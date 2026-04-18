package com.solv.wefin.web.game.turn.listener;

import com.solv.wefin.domain.game.turn.event.GameFinishedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

import static org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT;

@Slf4j
@Component
@RequiredArgsConstructor
public class GameFinishedEventListener {

    private final SimpMessagingTemplate messagingTemplate;

    @TransactionalEventListener(phase = AFTER_COMMIT)
    public void handle(GameFinishedEvent event) {
        String destination = "/topic/rooms/" + event.roomId() + "/turn";

        messagingTemplate.convertAndSend(destination, Map.of("type", "GAME_FINISHED"));

        log.info("[게임 종료 WS] roomId={}", event.roomId());
    }
}
