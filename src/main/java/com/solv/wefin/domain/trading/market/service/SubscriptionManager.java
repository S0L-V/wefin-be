package com.solv.wefin.domain.trading.market.service;

import com.solv.wefin.domain.trading.market.client.HantuWebSocketClient;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@RequiredArgsConstructor
@Service
public class SubscriptionManager {

    private static final int MAX_SUBSCRIPTION = 41;

    private final HantuWebSocketClient hantuWebSocketClient;
    private final Map<String, Integer> subscriptions = new HashMap<>();

    public synchronized void subscribe(String stockCode) {
        boolean isNew = !subscriptions.containsKey(stockCode);

        if (isNew && subscriptions.size() >= MAX_SUBSCRIPTION) {
            throw new BusinessException(ErrorCode.MARKET_SUBSCRIPTION_LIMIT_EXCEEDED);
        }

        int count = subscriptions.merge(stockCode, 1, Integer::sum);

        if (count == 1) {
            hantuWebSocketClient.sendSubscribe("H0STCNT0", stockCode);
            hantuWebSocketClient.sendSubscribe("H0STASP0", stockCode);
        }
    }

    public synchronized void unsubscribe(String stockCode) {
        Integer count = subscriptions.get(stockCode);

        if (count == null || count <= 0) {
            return;
        }

        if (count == 1) {
            hantuWebSocketClient.sendUnsubscribe("H0STCNT0", stockCode);
            hantuWebSocketClient.sendUnsubscribe("H0STASP0", stockCode);
            subscriptions.remove(stockCode);
        } else {
            subscriptions.put(stockCode, count - 1);
        }
    }
}
