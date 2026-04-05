package com.solv.wefin.domain.trading.market.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.solv.wefin.domain.trading.market.dto.TradeResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 한투 WebSocket 연결/재연결 클라이언트
 */
@RequiredArgsConstructor
@Slf4j
@Component
public class HantuWebSocketClient extends TextWebSocketHandler {

    @Value("${hantu.ws.url}")
    private String wsUrl;

    private final WebSocketClient hantuWsClient;
    private final HantuWebSocketKeyManager hantuWebSocketKeyManager;
    private WebSocketSession session;
    private final ObjectMapper objectMapper;
    private final SimpMessagingTemplate messagingTemplate;

    // 한투 WS에 연결
    @EventListener(ApplicationReadyEvent.class)
    public void connect() {
        hantuWsClient.execute(this, wsUrl);
    }

    // 연결 성공 시
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        this.session = session;
        log.info("한투 웹소켓 연결에 성공했습니다.");
    }

    // 메시지 수신 시
    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) {

    }

    // 연결 끊겼을 때 → 재연결
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.warn("한투 웹소켓 연결 끊김. 재연결 시도...");
        try {
            Thread.sleep(5000);
            connect();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("한투 웹소켓 재연결 중 인터럽트 발생");
        }
    }

    // 종목 구독 요청
    public void sendSubscribe(String stockCode) {
        // WEF-356에서 구현
    }

    // 종목 구독 해제 요청
    public void sendUnsubscribe(String stockCode) {
        // WEF-356에서 구현
    }

    private void sendMessage(String stockCode, String trType) {
        // WEF-356에서 구현
    }
}
