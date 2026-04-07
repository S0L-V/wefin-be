package com.solv.wefin.domain.trading.market.dto;

import java.math.BigDecimal;

/**
 * 실시간 체결가 WebSocket push 응답
 */
public record TradeResponse(
        WebSocketMessageType type,
        String stockCode,
        BigDecimal currentPrice,
        BigDecimal changePrice,
        BigDecimal changeRate,
        long tradeVolume,
        long totalVolume,
        String tradeTime,
        String tradeSide        // "1": 매수, "5": 매도
) {}