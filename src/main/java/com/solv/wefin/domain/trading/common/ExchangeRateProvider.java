package com.solv.wefin.domain.trading.common;

import java.math.BigDecimal;

public interface ExchangeRateProvider {
    BigDecimal getUsdKrwRate();
}
