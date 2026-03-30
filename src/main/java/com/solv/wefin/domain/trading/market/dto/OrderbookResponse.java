package com.solv.wefin.domain.trading.market.dto;

import java.util.List;

public record OrderbookResponse(
        List<OrderbookEntry> asks,   // 매도호가 10개
        List<OrderbookEntry> bids,   // 매수호가 10개
        long totalAskQuantity,       // 총 매도 잔량
        long totalBidQuantity        // 총 매수 잔량
) {
    public record OrderbookEntry(
            int price,
            long quantity
    ) {
    }
}
