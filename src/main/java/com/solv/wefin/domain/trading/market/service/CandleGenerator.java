package com.solv.wefin.domain.trading.market.service;

import com.solv.wefin.domain.trading.market.dto.MinuteCandleResponse;
import com.solv.wefin.domain.trading.market.dto.WebSocketMessageType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
@RequiredArgsConstructor
public class CandleGenerator {

    private final SimpMessagingTemplate messagingTemplate;

    // period 목록
    private static final int[] PERIODS = {1, 5, 15, 30, 60};

    // 종목별 현재 생성중인 분봉
    private final ConcurrentHashMap<String, CandleData> currentCandles = new ConcurrentHashMap<>();

    // 체결가 수신 시 호출
    public void onTrade(String stockCode, BigDecimal price, long volume, String tradeTime) {
        if (tradeTime == null || tradeTime.length() < 4 || !tradeTime.substring(0, 4).matches("\\d{4}")) {
            log.warn("유효하지 않은 tradeTime: stockCode={}, tradeTime={}", stockCode, tradeTime);
            return;
        }

        int hour = Integer.parseInt(tradeTime.substring(0, 2));
        int minute = Integer.parseInt(tradeTime.substring(2, 4));

        for (int period : PERIODS) {
            int floored = (minute / period) * period;
            String timeKey = String.format("%02d%02d", hour, floored);
            String mapKey = stockCode + ":" + period;

            final AtomicReference<CandleData> toPush = new AtomicReference<>();

            currentCandles.compute(mapKey, (key, existing) -> {
                if (existing == null || !existing.timeKey.equals(timeKey)) {
                    if (existing != null) {
                        toPush.set(existing);
                    }
                    return new CandleData(timeKey, period, price, price, price, price, volume);
                }
                existing.update(price, volume);
                return existing;
            });

            if (toPush.get() != null) {
                pushCandle(stockCode, toPush.get());
            }
        }
    }

    private void pushCandle(String stockCode, CandleData data) {
        log.info("분봉 확정 push: {} period={}m time={} O={} H={} L={} C={} V={}",
                stockCode, data.period, data.timeKey,
                data.open, data.high, data.low, data.close, data.volume);

        LocalDateTime time = LocalDate.now(ZoneId.of("Asia/Seoul"))
                .atTime(Integer.parseInt(data.timeKey.substring(0, 2)),
                        Integer.parseInt(data.timeKey.substring(2, 4)));

        MinuteCandleResponse response = new MinuteCandleResponse(
                WebSocketMessageType.CANDLE,
                stockCode,
                time,
                data.open, data.high, data.low, data.close,
                data.volume,
                String.valueOf(data.period)
        );
        messagingTemplate.convertAndSend("/topic/stocks/" + stockCode + "/candle", response);
    }

    private static class CandleData {
        String timeKey;    // "1125"
        int period;        // 5
        BigDecimal open, high, low, close;
        long volume;

        CandleData(String timeKey, int period, BigDecimal open, BigDecimal high,
                   BigDecimal low, BigDecimal close, long volume) {
            this.timeKey = timeKey;
            this.period = period;
            this.open = open;
            this.high = high;
            this.low = low;
            this.close = close;
            this.volume = volume;
        }

        void update(BigDecimal price, long tradeVolume) {
            if (price.compareTo(high) > 0) high = price;
            if (price.compareTo(low) < 0) low = price;
            close = price;
            volume += tradeVolume;
        }
    }

    @Scheduled(cron = "0 30 15 * * MON-FRI", zone = "Asia/Seoul")
    public void flushAll() {
        log.info("장 마감 분봉 flush 시작");
        currentCandles.forEach((mapKey, data) -> {
            // mapKey: "005930:5" → stockCode 추출
            String stockCode = mapKey.substring(0, mapKey.indexOf(':'));
            pushCandle(stockCode, data);
        });
        currentCandles.clear();
    }
}
