package com.solv.wefin.domain.trading.market.service;

import com.solv.wefin.domain.trading.market.client.HantuMarketClient;
import com.solv.wefin.domain.trading.market.client.dto.HantuStockInfoApiResponse;
import com.solv.wefin.domain.trading.market.dto.StockBasicInfo;
import com.solv.wefin.domain.trading.stock.service.StockService;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockBasicInfoService {

    private final StockService stockService;
    private final HantuMarketClient hantuMarketClient;

    @Cacheable(cacheNames = "stockBasicInfo", key = "#stockCode")
    public StockBasicInfo getBasicInfo(String stockCode) {
        if (!stockService.existsByCode(stockCode)) {
            throw new BusinessException(ErrorCode.MARKET_STOCK_NOT_FOUND);
        }

        HantuStockInfoApiResponse response;
        try {
            response = hantuMarketClient.fetchStockInfo(stockCode);
        } catch (Exception e) {
            log.error("한투 기본정보 조회 실패: type={}", e.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.MARKET_API_FAILED);
        }

        if (response == null || response.output() == null) {
            throw new BusinessException(ErrorCode.MARKET_API_FAILED);
        }

        return StockBasicInfo.from(response.output());
    }
}
