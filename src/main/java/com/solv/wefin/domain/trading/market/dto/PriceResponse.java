package com.solv.wefin.domain.trading.market.dto;

public record PriceResponse(
        String stockCode,
        int currentPrice,
        int changePrice,
        float changeRate,
        long volume,
        int openPrice,
        int highPrice,
        int lowPrice
) {}
