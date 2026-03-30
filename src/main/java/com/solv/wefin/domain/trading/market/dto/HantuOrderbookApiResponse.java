package com.solv.wefin.domain.trading.market.dto;

public record HantuOrderbookApiResponse(
        Output1 output1
) {
    public record Output1(
            String askp1,          // 매도호가1
            String askp2,
            String askp3,
            String askp4,
            String askp5,
            String askp6,
            String askp7,
            String askp8,
            String askp9,
            String askp10,
            String bidp1,          // 매수호가1
            String bidp2,
            String bidp3,
            String bidp4,
            String bidp5,
            String bidp6,
            String bidp7,
            String bidp8,
            String bidp9,
            String bidp10,
            String askp_rsqn1,     // 매도호가 잔량1
            String askp_rsqn2,
            String askp_rsqn3,
            String askp_rsqn4,
            String askp_rsqn5,
            String askp_rsqn6,
            String askp_rsqn7,
            String askp_rsqn8,
            String askp_rsqn9,
            String askp_rsqn10,
            String bidp_rsqn1,     // 매수호가 잔량1
            String bidp_rsqn2,
            String bidp_rsqn3,
            String bidp_rsqn4,
            String bidp_rsqn5,
            String bidp_rsqn6,
            String bidp_rsqn7,
            String bidp_rsqn8,
            String bidp_rsqn9,
            String bidp_rsqn10,
            String total_askp_rsqn, // 총 매도호가 잔량
            String total_bidp_rsqn  // 총 매수호가 잔량
    ) {}
}
