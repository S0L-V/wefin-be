package com.solv.wefin.domain.trading.market.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MinuteCandleResponse(
        WebSocketMessageType type,
        String stockCode,
        LocalDateTime time,
        BigDecimal openPrice,
        BigDecimal highPrice,
        BigDecimal lowPrice,
        BigDecimal closePrice,
        long volume
) {}
