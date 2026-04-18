package com.solv.wefin.domain.trading.common;

import java.math.BigDecimal;

public interface MarketPriceProvider {
    BigDecimal getCurrentPrice(String stockCode);
    String getCurrency(String stockCode);
}
