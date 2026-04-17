package com.solv.wefin.domain.trading.market.service;

import com.solv.wefin.domain.trading.market.client.HantuMarketClient;
import com.solv.wefin.domain.trading.market.client.dto.HantuInvestorTrendApiResponse;
import com.solv.wefin.domain.trading.market.dto.InvestorTrendResponse;
import com.solv.wefin.domain.trading.stock.service.StockService;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

@Slf4j
@Service
@RequiredArgsConstructor
public class InvestorTrendService {

    private final StockService stockService;
    private final HantuMarketClient hantuMarketClient;

    @Cacheable(cacheNames = "investorTrend", key = "#stockCode")
    public InvestorTrendResponse getInvestorTrend(String stockCode) {
        if (!stockService.existsByCode(stockCode)) {
            throw new BusinessException(ErrorCode.MARKET_STOCK_NOT_FOUND);
        }

        HantuInvestorTrendApiResponse response;
        try {
            response = hantuMarketClient.fetchInvestorTrend(stockCode);
        } catch (RestClientException e) {
            log.error("한투 투자자 매매동향 조회 실패: stockCode={}, type={}",
                    stockCode, e.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.MARKET_API_FAILED);
        }

        if (response == null) {
            throw new BusinessException(ErrorCode.MARKET_API_FAILED);
        }
        return InvestorTrendResponse.from(stockCode, response);
    }
}
