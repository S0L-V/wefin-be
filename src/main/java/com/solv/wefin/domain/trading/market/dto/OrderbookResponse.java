package com.solv.wefin.domain.trading.market.dto;

import com.solv.wefin.domain.trading.market.client.dto.HantuOrderbookApiResponse;

import java.util.List;

public record OrderbookResponse(
        String type,
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

    private static OrderbookEntry toEntry(String price, String quantity) {
        return new OrderbookEntry(Integer.parseInt(price), Long.parseLong(quantity));
    }

    public static OrderbookResponse from(HantuOrderbookApiResponse.Output1 output) {
        List<OrderbookResponse.OrderbookEntry> asks = List.of(
                toEntry(output.askp1(), output.askp_rsqn1()),
                toEntry(output.askp2(), output.askp_rsqn2()),
                toEntry(output.askp3(), output.askp_rsqn3()),
                toEntry(output.askp4(), output.askp_rsqn4()),
                toEntry(output.askp5(), output.askp_rsqn5()),
                toEntry(output.askp6(), output.askp_rsqn6()),
                toEntry(output.askp7(), output.askp_rsqn7()),
                toEntry(output.askp8(), output.askp_rsqn8()),
                toEntry(output.askp9(), output.askp_rsqn9()),
                toEntry(output.askp10(), output.askp_rsqn10())
        );

        List<OrderbookResponse.OrderbookEntry> bids = List.of(
                toEntry(output.bidp1(), output.bidp_rsqn1()),
                toEntry(output.bidp2(), output.bidp_rsqn2()),
                toEntry(output.bidp3(), output.bidp_rsqn3()),
                toEntry(output.bidp4(), output.bidp_rsqn4()),
                toEntry(output.bidp5(), output.bidp_rsqn5()),
                toEntry(output.bidp6(), output.bidp_rsqn6()),
                toEntry(output.bidp7(), output.bidp_rsqn7()),
                toEntry(output.bidp8(), output.bidp_rsqn8()),
                toEntry(output.bidp9(), output.bidp_rsqn9()),
                toEntry(output.bidp10(), output.bidp_rsqn10())
        );

        return new OrderbookResponse(
                "ORDERBOOK",
                asks,
                bids,
                Long.parseLong(output.total_askp_rsqn()),
                Long.parseLong(output.total_bidp_rsqn())
        );
    }
}
