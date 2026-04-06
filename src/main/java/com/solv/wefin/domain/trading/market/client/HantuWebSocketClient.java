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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final HantuWebSocketKeyManager hantuWebSocketKeyManager;
    private volatile WebSocketSession session;
    private final ObjectMapper objectMapper;
    private final SimpMessagingTemplate messagingTemplate;

    // 한투 WS에 연결
    @EventListener(ApplicationReadyEvent.class)
    public void connect() {
        try {
            hantuWsClient.execute(this, wsUrl);
        } catch (Exception e) {
            log.error("한투 웹소켓 초기 연결 실패. 5초 후 재시도...", e);
            scheduler.schedule(this::connect, 5, TimeUnit.SECONDS);

        }
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
        String payload = message.getPayload();

        // JSON 응답 (구독 확인) → 로그만
        if (payload.startsWith("{")) {
            log.info("한투 WS 응답: {}", payload);
            return;
        }

        // 실시간 데이터: 0|H0STCNT0|004|데이터
        String[] parts = payload.split("\\|");
        if (parts.length < 4) return;

        String[] fields = parts[3].split("\\^");
        int fieldCount = 46;
        int recordCount = Integer.parseInt(parts[2]);

        for (int i = 0; i < recordCount; i++) {
            int offset = i * fieldCount;

            TradeResponse response = new TradeResponse(
                    fields[offset],                           // stockCode
                    new BigDecimal(fields[offset + 2]),       // currentPrice
                    new BigDecimal(fields[offset + 4]),       // changePrice
                    new BigDecimal(fields[offset + 5]),       // changeRate
                    Long.parseLong(fields[offset + 12]),      // tradeVolume
                    Long.parseLong(fields[offset + 13]),      // totalVolume
                    fields[offset + 1]                        // tradeTime
            );

            messagingTemplate.convertAndSend("/topic/stocks/" + response.stockCode(), response);
        }
    }

    // 연결 끊겼을 때 → 재연결
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.warn("한투 웹소켓 연결 끊김. 재연결 시도...");
        scheduler.schedule(this::connect, 5, TimeUnit.SECONDS);
    }

    // 종목 구독 요청
    public void sendSubscribe(String trId, String stockCode) {
        sendMessage(trId, stockCode, "1");
    }

    // 종목 구독 해제 요청
    public void sendUnsubscribe(String trId, String stockCode) {
        sendMessage(trId, stockCode, "2");
    }

    private void sendMessage(String trId, String stockCode, String trType) {
        try {
            Map<String, Object> message = Map.of(
                    "header", Map.of(
                            "approval_key", hantuWebSocketKeyManager.getApprovalKey(),
                            "custtype", "P",
                            "tr_type", trType,
                            "content-type", "utf-8"
                    ),
                    "body", Map.of(
                            "input", Map.of(
                                    "tr_id", trId,
                                    "tr_key", stockCode
                            )
                    )
            );

            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(message)));
        } catch (Exception e) {
            log.error("한투 웹소켓 메시지 전송 실패: {}", stockCode, e);
        }
    }
}
