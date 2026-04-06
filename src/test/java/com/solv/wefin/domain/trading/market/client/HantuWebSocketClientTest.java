package com.solv.wefin.domain.trading.market.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.solv.wefin.domain.trading.market.dto.OrderbookResponse;
import com.solv.wefin.domain.trading.market.dto.TradeResponse;
import com.solv.wefin.domain.trading.market.service.MarketService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.WebSocketClient;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatCode;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class HantuWebSocketClientTest {

    @Mock
    private WebSocketClient hantuWsClient;
    @Mock
    private HantuWebSocketKeyManager hantuWebSocketKeyManager;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private MarketService marketService;
    @Mock
    private WebSocketSession session;

    private HantuWebSocketClient client;

    @BeforeEach
    void setUp() {
        client = new HantuWebSocketClient(
                hantuWsClient,
                hantuWebSocketKeyManager,
                objectMapper,
                messagingTemplate,
                marketService
        );
    }

    @Test
    void 체결_데이터_수신시_올바른_TradeResponse_전송() throws Exception {
        // 46개 필드: [0]종목코드, [1]체결시간, [2]현재가, [4]전일대비, [5]등락률,
        //           [7]시가, [8]고가, [9]저가, [12]체결거래량, [13]누적거래량
        String[] fields = new String[46];
        fields[0] = "005930";
        fields[1] = "153005";
        fields[2] = "97500";
        fields[4] = "1200";
        fields[5] = "1.25";
        fields[7] = "96800";
        fields[8] = "98200";
        fields[9] = "96300";
        fields[12] = "342";
        fields[13] = "12340567";
        // 나머지 필드 빈 값 채우기
        for (int i = 0; i < fields.length; i++) {
            if (fields[i] == null) fields[i] = "0";
        }

        String data = String.join("^", fields);
        String payload = "0|H0STCNT0|001|" + data;

        client.handleTextMessage(session, new TextMessage(payload));

        verify(messagingTemplate).convertAndSend(
                eq("/topic/stocks/005930"),
                argThat((TradeResponse r) ->
                        r.type().equals("TRADE")
                                && r.stockCode().equals("005930")
                                && r.currentPrice().intValue() == 97500
                                && r.changePrice().intValue() == 1200
                                && r.tradeVolume() == 342
                )
        );
    }

    @Test
    void 호가_데이터_수신시_올바른_OrderbookResponse_전송() throws Exception {
        // [0]종목코드, [3~12]매도호가1~10, [13~22]매수호가1~10,
        // [23~32]매도잔량1~10, [33~42]매수잔량1~10, [43]총매도잔량, [44]총매수잔량
        String[] fields = new String[45];
        fields[0] = "005930";
        for (int i = 1; i < 3; i++) fields[i] = "0";

        // 매도호가 1~10
        for (int i = 0; i < 10; i++) fields[3 + i] = String.valueOf(98000 + i * 100);
        // 매수호가 1~10
        for (int i = 0; i < 10; i++) fields[13 + i] = String.valueOf(97900 - i * 100);
        // 매도잔량 1~10
        for (int i = 0; i < 10; i++) fields[23 + i] = String.valueOf(100 + i);
        // 매수잔량 1~10
        for (int i = 0; i < 10; i++) fields[33 + i] = String.valueOf(200 + i);

        fields[43] = "1045";  // 총 매도잔량
        fields[44] = "2045";  // 총 매수잔량

        String data = String.join("^", fields);
        String payload = "0|H0STASP0|001|" + data;

        client.handleTextMessage(session, new TextMessage(payload));

        verify(messagingTemplate).convertAndSend(
                eq("/topic/stocks/005930/orderbook"),
                argThat((OrderbookResponse r) ->
                        r.type().equals("ORDERBOOK")
                                && r.asks().size() == 10
                                && r.bids().size() == 10
                                && r.asks().get(0).price() == 98000
                                && r.bids().get(0).price() == 97900
                                && r.totalAskQuantity() == 1045
                                && r.totalBidQuantity() == 2045
                )
        );
    }

    @Test
    void 잘못된_데이터_수신시_예외없이_처리() throws Exception {
        String payload = "0|H0STCNT0|001|잘못된데이터";

        assertThatCode(() -> client.handleTextMessage(session, new TextMessage(payload)))
                .doesNotThrowAnyException();

        verify(messagingTemplate, never()).convertAndSend(anyString(),
                any(TradeResponse.class));
    }
}