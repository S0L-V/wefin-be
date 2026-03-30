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

@RequiredArgsConstructor
@Service
public class MarketService implements MarketPriceProvider, ExchangeRateProvider {

    private final HantuMarketClient hantuMarketClient;

    public PriceResponse getPrice(String stockCode) {
        HantuPriceApiResponse.Output output = hantuMarketClient.fetchCurrentPrice(stockCode).output();
        return PriceResponse.from(stockCode, output);
    }

    public OrderbookResponse getOrderbook(String stockCode) {
        HantuOrderbookApiResponse.Output1 output = hantuMarketClient.fetchOrderbook(stockCode).output1();
        return OrderbookResponse.from(output);
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
