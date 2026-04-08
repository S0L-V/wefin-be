package com.solv.wefin.domain.trading.market.service;

import com.solv.wefin.domain.trading.market.dto.MinuteCandleResponse;
import com.solv.wefin.domain.trading.market.dto.WebSocketMessageType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class CandleGenerator {

    private final SimpMessagingTemplate messagingTemplate;

    // 종목별 현재 생성중인 분봉
    private final ConcurrentHashMap<String, MinuteCandleData> currentCandles = new ConcurrentHashMap<>();

    // 체결가 수신 시 호출
    public void onTrade(String stockCode, BigDecimal price, long volume, String tradeTime) {
        if (tradeTime == null || tradeTime.length() < 4) {
            log.warn("유효하지 않은 tradeTime: stockCode={}, tradeTime={}", stockCode, tradeTime);
            return;
        }
        // tradeTime "112654" → 분 키 "1126" 추출
        String minuteKey = tradeTime.substring(0, 4);

        // onTrade 시작: 체결 수신 확인
        // log.debug("캔들 체결 수신: {} price={} minute={}", stockCode, price, minuteKey);

        currentCandles.compute(stockCode, (key, existing) -> {
            if (existing == null || !existing.minuteKey.equals(minuteKey)) {
                if (existing != null) {
                    pushCandle(stockCode, existing);
                }
                return new MinuteCandleData(minuteKey, price, price, price, price, volume);
            }

            existing.update(price, volume);
            return existing;
        });
    }

    private void pushCandle(String stockCode, MinuteCandleData data) {
        // pushCandle: 분봉 확정 push
        log.info("분봉 확정 push: {} time={} O={} H={} L={} C={} V={}",
                stockCode, data.minuteKey, data.open, data.high, data.low, data.close, data.volume);

        // "1126" → 오늘 날짜 11:26:00
        LocalDateTime time = LocalDate.now()
                .atTime(Integer.parseInt(data.minuteKey.substring(0, 2)),
                        Integer.parseInt(data.minuteKey.substring(2, 4)));

        MinuteCandleResponse response = new MinuteCandleResponse(
                WebSocketMessageType.CANDLE,
                stockCode,
                time,
                data.open, data.high, data.low, data.close,
                data.volume
        );
        messagingTemplate.convertAndSend("/topic/stocks/" + stockCode + "/candle", response);
    }


    // 분봉 데이터를 담는 내부 클래스
    private static class MinuteCandleData {
        String minuteKey;  // "1126"
        BigDecimal open, high, low, close;
        long volume;

        MinuteCandleData(String minuteKey, BigDecimal open, BigDecimal high, BigDecimal low,
                         BigDecimal close, long volume) {
            this.minuteKey = minuteKey;
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
}

