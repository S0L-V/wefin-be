package com.solv.wefin.domain.trading.market.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.solv.wefin.domain.trading.market.dto.OrderbookResponse;
import com.solv.wefin.domain.trading.market.dto.PriceResponse;
import com.solv.wefin.domain.trading.market.dto.TradeResponse;
import com.solv.wefin.domain.trading.market.service.MarketService;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
    private final MarketService marketService;
    private final Set<String> subscribedStocks = ConcurrentHashMap.newKeySet();

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

        // sendSubscribe() 호출x (subscribedStocks.add() 중복 실행 방지)
        subscribedStocks.forEach(stockCode -> {
            sendMessage("H0STCNT0", stockCode, "1");
            sendMessage("H0STASP0", stockCode, "1");
            log.info("종목 재구독: {}", stockCode);
        });
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

        // 실시간 데이터: 0|tr_id|004|데이터
        String[] parts = payload.split("\\|");
        if (parts.length < 4) return;

        String trId = parts[1];
        String data = parts[3];

        try {
            if ("H0STCNT0".equals(trId)) {
                parseAndSendTrade(data, Integer.parseInt(parts[2]));
            } else if ("H0STASP0".equals(trId)) {
                parseAndSendOrderbook(data);
            }
        } catch (Exception e) {
            log.error("한투 실시간 데이터 파싱 실패 [trId={}, data={}]", trId, data, e);
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
        subscribedStocks.add(stockCode);
        sendMessage(trId, stockCode, "1");
    }

    // 종목 구독 해제 요청
    public void sendUnsubscribe(String trId, String stockCode) {
        subscribedStocks.remove(stockCode);
        sendMessage(trId, stockCode, "2");
    }

    private void sendMessage(String trId, String stockCode, String trType) {
        if (session == null || !session.isOpen()) {
            log.warn("한투 웹소켓 미연결 상태. 메시지 전송 스킵: {}", stockCode);
            return;
        }

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

    private void parseAndSendTrade(String data, int recordCount) {
        String[] fields = data.split("\\^");
        int fieldCount = 46;

        if (fields.length < recordCount * fieldCount) {
            log.warn("체결 데이터 필드 수 부족: expected={}, actual={}", recordCount * fieldCount, fields.length);
            return;
        }

        for (int i = 0; i < recordCount; i++) {
            int offset = i * fieldCount;
            TradeResponse response = new TradeResponse(
                    "TRADE",
                    fields[offset],                           // stockCode
                    new BigDecimal(fields[offset + 2]),       // currentPrice
                    new BigDecimal(fields[offset + 4]),       // changePrice
                    new BigDecimal(fields[offset + 5]),       // changeRate
                    Long.parseLong(fields[offset + 12]),      // tradeVolume
                    Long.parseLong(fields[offset + 13]),      // totalVolume
                    fields[offset + 1]                        // tradeTime
            );
            messagingTemplate.convertAndSend("/topic/stocks/" + response.stockCode(), response);

            PriceResponse priceResponse = new PriceResponse(
                    fields[offset],
                    Integer.parseInt(fields[offset + 2]),
                    Integer.parseInt(fields[offset + 4]),
                    Float.parseFloat(fields[offset + 5]),
                    Long.parseLong(fields[offset + 13]),
                    Integer.parseInt(fields[offset + 7]),
                    Integer.parseInt(fields[offset + 8]),
                    Integer.parseInt(fields[offset + 9])
            );
            marketService.updatePriceCache(fields[offset], priceResponse);
        }
    }

    private void parseAndSendOrderbook(String data) {
        String[] fields = data.split("\\^");

        if (fields.length < 45) {
            log.warn("호가 데이터 필드 수 부족: expected=45, actual={}", fields.length);
            return;
        }

        String stockCode = fields[0];

        List<OrderbookResponse.OrderbookEntry> asks = new ArrayList<>();
        List<OrderbookResponse.OrderbookEntry> bids = new ArrayList<>();

        for (int i = 0; i < 10; i++) {
            asks.add(new OrderbookResponse.OrderbookEntry(
                    Integer.parseInt(fields[3 + i]),      // ASKP1~10
                    Long.parseLong(fields[23 + i])         // ASKP_RSQN1~10
            ));
            bids.add(new OrderbookResponse.OrderbookEntry(
                    Integer.parseInt(fields[13 + i]),      // BIDP1~10
                    Long.parseLong(fields[33 + i])         // BIDP_RSQN1~10
            ));
        }

        OrderbookResponse response = new OrderbookResponse(
                "ORDERBOOK",
                asks, bids,
                Long.parseLong(fields[43]),                // TOTAL_ASKP_RSQN
                Long.parseLong(fields[44])                 // TOTAL_BIDP_RSQN
        );

        messagingTemplate.convertAndSend("/topic/stocks/" + stockCode + "/orderbook",
                response);
    }
}
