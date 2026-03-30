package com.solv.wefin.domain.trading.market.service;

import com.solv.wefin.domain.trading.common.ExchangeRateProvider;
import com.solv.wefin.domain.trading.market.client.HantuMarketClient;
import com.solv.wefin.domain.trading.market.dto.HantuOrderbookApiResponse;
import com.solv.wefin.domain.trading.market.dto.HantuPriceApiResponse;
import com.solv.wefin.domain.trading.market.dto.OrderbookResponse;
import com.solv.wefin.domain.trading.market.dto.PriceResponse;
import com.solv.wefin.domain.trading.common.MarketPriceProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@RequiredArgsConstructor
@Service
public class MarketService implements MarketPriceProvider, ExchangeRateProvider {

    private final HantuMarketClient hantuMarketClient;

    public PriceResponse getPrice(String stockCode) {
        HantuPriceApiResponse.Output output = hantuMarketClient.fetchCurrentPrice(stockCode).output();

        return new PriceResponse(
                stockCode,
                Integer.parseInt(output.stck_prpr()),
                Integer.parseInt(output.prdy_vrss()),
                Float.parseFloat(output.prdy_ctrt()),
                Long.parseLong(output.acml_vol()),
                Integer.parseInt(output.stck_oprc()),
                Integer.parseInt(output.stck_hgpr()),
                Integer.parseInt(output.stck_lwpr())
        );
    }

    public OrderbookResponse getOrderbook(String stockCode) {
        HantuOrderbookApiResponse.Output1 output = hantuMarketClient.fetchOrderbook(stockCode).output1();

        List<OrderbookResponse.OrderbookEntry> asks = List.of(
                new OrderbookResponse.OrderbookEntry(
                        Integer.parseInt(output.askp1()),
                        Long.parseLong(output.askp_rsqn1())
                ),
                new OrderbookResponse.OrderbookEntry(
                        Integer.parseInt(output.askp2()),
                        Long.parseLong(output.askp_rsqn2())
                ),
                new OrderbookResponse.OrderbookEntry(
                        Integer.parseInt(output.askp3()),
                        Long.parseLong(output.askp_rsqn3())
                ),
                new OrderbookResponse.OrderbookEntry(
                        Integer.parseInt(output.askp4()),
                        Long.parseLong(output.askp_rsqn4())
                ),
                new OrderbookResponse.OrderbookEntry(
                        Integer.parseInt(output.askp5()),
                        Long.parseLong(output.askp_rsqn5())
                ),
                new OrderbookResponse.OrderbookEntry(
                        Integer.parseInt(output.askp6()),
                        Long.parseLong(output.askp_rsqn6())
                ),
                new OrderbookResponse.OrderbookEntry(
                        Integer.parseInt(output.askp7()),
                        Long.parseLong(output.askp_rsqn7())
                ),
                new OrderbookResponse.OrderbookEntry(
                        Integer.parseInt(output.askp8()),
                        Long.parseLong(output.askp_rsqn8())
                ),
                new OrderbookResponse.OrderbookEntry(
                        Integer.parseInt(output.askp9()),
                        Long.parseLong(output.askp_rsqn9())
                ),
                new OrderbookResponse.OrderbookEntry(
                        Integer.parseInt(output.askp10()),
                        Long.parseLong(output.askp_rsqn10())
                )
        );

        List<OrderbookResponse.OrderbookEntry> bids = List.of(
                new OrderbookResponse.OrderbookEntry(
                        Integer.parseInt(output.bidp1()),
                        Long.parseLong(output.bidp_rsqn1())
                ),
                new OrderbookResponse.OrderbookEntry(
                        Integer.parseInt(output.bidp2()),
                        Long.parseLong(output.bidp_rsqn2())
                ),
                new OrderbookResponse.OrderbookEntry(
                        Integer.parseInt(output.bidp3()),
                        Long.parseLong(output.bidp_rsqn3())
                ),
                new OrderbookResponse.OrderbookEntry(
                        Integer.parseInt(output.bidp4()),
                        Long.parseLong(output.bidp_rsqn4())
                ),
                new OrderbookResponse.OrderbookEntry(
                        Integer.parseInt(output.bidp5()),
                        Long.parseLong(output.bidp_rsqn5())
                ),
                new OrderbookResponse.OrderbookEntry(
                        Integer.parseInt(output.bidp6()),
                        Long.parseLong(output.bidp_rsqn6())
                ),
                new OrderbookResponse.OrderbookEntry(
                        Integer.parseInt(output.bidp7()),
                        Long.parseLong(output.bidp_rsqn7())
                ),
                new OrderbookResponse.OrderbookEntry(
                        Integer.parseInt(output.bidp8()),
                        Long.parseLong(output.bidp_rsqn8())
                ),
                new OrderbookResponse.OrderbookEntry(
                        Integer.parseInt(output.bidp9()),
                        Long.parseLong(output.bidp_rsqn9())
                ),
                new OrderbookResponse.OrderbookEntry(
                        Integer.parseInt(output.bidp10()),
                        Long.parseLong(output.bidp_rsqn10())
                )
        );

        return new OrderbookResponse(
                asks,
                bids,
                Long.parseLong(output.total_askp_rsqn()),
                Long.parseLong(output.total_bidp_rsqn())
        );
    }

    @Override
    public BigDecimal getCurrentPrice(String stockCode) {
        PriceResponse response = getPrice(stockCode);
        return BigDecimal.valueOf(response.currentPrice());
    }

    @Override
    public String getCurrency(String stockCode) {
        return "KRW";
    }

    @Override
    public BigDecimal getUsdKrwRate() {
        return new BigDecimal("1508.00");
    }
}
