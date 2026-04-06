package com.solv.wefin.domain.trading.market.service;

import com.solv.wefin.domain.trading.market.client.HantuWebSocketClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@RequiredArgsConstructor
@Service
public class SubscriptionManager {

    private final HantuWebSocketClient hantuWebSocketClient;
    private final ConcurrentHashMap<String, AtomicInteger> subscriptions = new ConcurrentHashMap<>();

    public void subscribe(String stockCode) {

        AtomicInteger count = subscriptions.computeIfAbsent
                (stockCode, k -> new AtomicInteger(0));

        if (count.incrementAndGet() == 1) {
            hantuWebSocketClient.sendSubscribe(stockCode);
        }
    }

    public void unsubscribe(String stockCode) {
        AtomicInteger count = subscriptions.get(stockCode);

        if (count == null) {
            return;
        }

        if (count.decrementAndGet() == 0) {
            hantuWebSocketClient.sendUnsubscribe(stockCode);
            subscriptions.remove(stockCode);
        }

    }
}
