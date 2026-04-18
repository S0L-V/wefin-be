package com.solv.wefin.web.trading.market;

import com.solv.wefin.domain.trading.market.service.SubscriptionManager;
import com.solv.wefin.web.trading.market.dto.request.StockSubscribeRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

@Slf4j
@Controller
@RequiredArgsConstructor
public class MarketWebSocketController {

    private final SubscriptionManager subscriptionManager;

    @MessageMapping("/stocks/subscribe")
    public void subscribe(StockSubscribeRequest request) {
        log.info("종목 구독 요청: {}", request.stockCode());
        subscriptionManager.subscribe(request.stockCode());
    }

    @MessageMapping("/stocks/unsubscribe")
    public void unsubscribe(StockSubscribeRequest request) {
        log.info("종목 구독 해제 요청: {}", request.stockCode());
        subscriptionManager.unsubscribe(request.stockCode());
    }
}
