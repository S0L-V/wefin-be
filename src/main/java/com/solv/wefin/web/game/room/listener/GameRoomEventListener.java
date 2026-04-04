package com.solv.wefin.web.game.room.listener;

import com.solv.wefin.domain.game.room.event.GameRoomEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import static org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT;


@Slf4j
@Component
@RequiredArgsConstructor
public class GameRoomEventListener {

    private final SimpMessagingTemplate messagingTemplate;

    //트랜잭션 커밋 후에만  브로드캐스트
    @TransactionalEventListener(phase = AFTER_COMMIT)
    public void handle(GameRoomEvent event) {
        //구독은 /topic/  or  /queue/
        String destination = "/topic/room/" + event.roomId();
        log.info("Broadcasting {} to {}", event.type(), destination);
        messagingTemplate.convertAndSend(destination, event.type());
    }
}
