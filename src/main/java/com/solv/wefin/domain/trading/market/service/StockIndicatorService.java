package com.solv.wefin.domain.trading.market.service;

import com.solv.wefin.domain.trading.dart.dto.DartFinancialPeriod;
import com.solv.wefin.domain.trading.dart.dto.DartFinancialSummary;
import com.solv.wefin.domain.trading.dart.service.DartFinancialService;
import com.solv.wefin.domain.trading.market.client.HantuMarketClient;
import com.solv.wefin.domain.trading.market.client.dto.HantuStockInfoApiResponse;
import com.solv.wefin.domain.trading.market.dto.StockIndicatorInfo;
import com.solv.wefin.domain.trading.stock.service.StockService;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockIndicatorService {

    private static final int ROE_SCALE = 2;
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    private final StockService stockService;
    private final HantuMarketClient hantuMarketClient;
    private final DartFinancialService dartFinancialService;

    @Cacheable(cacheNames = "stockIndicator", key = "#stockCode")
    public StockIndicatorInfo getIndicator(String stockCode) {
        if (!stockService.existsByCode(stockCode)) {
            throw new BusinessException(ErrorCode.MARKET_STOCK_NOT_FOUND);
        }

        HantuStockInfoApiResponse response;
        try {
            response = hantuMarketClient.fetchStockInfo(stockCode);
        } catch (RestClientException e) {
            log.error("한투 투자지표 조회 실패: type={}", e.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.MARKET_API_FAILED);
        }

        if (response == null || response.output() == null) {
            throw new BusinessException(ErrorCode.MARKET_API_FAILED);
        }

        HantuStockInfoApiResponse.Output output = response.output();
        return new StockIndicatorInfo(
                parseBigDecimal(output.per()),
                parseBigDecimal(output.pbr()),
                parseBigDecimal(output.eps()),
                calculateRoe(stockCode)
        );
    }

    /**
     * ROE = 당기순이익 / 자본총계 × 100 (%)
     * DART 재무제표 기반. DART 실패 시 null 반환 (다른 지표는 영향 없이 제공).
     */
    private BigDecimal calculateRoe(String stockCode) {
        try {
            DartFinancialSummary summary = dartFinancialService.getFinancialSummary(stockCode);
            if (summary == null) return null;
            DartFinancialPeriod current = summary.currentPeriod();
            if (current == null) return null;

            BigDecimal netIncome = current.netIncome();
            BigDecimal totalEquity = current.totalEquity();
            if (netIncome == null || totalEquity == null
                    || totalEquity.compareTo(BigDecimal.ZERO) == 0) {
                return null;
            }

            return netIncome.multiply(ONE_HUNDRED)
                    .divide(totalEquity, ROE_SCALE, RoundingMode.HALF_UP);
        } catch (BusinessException e) {
            log.warn("ROE 계산 실패 (DART 재무제표 미존재/실패): stockCode={}, code={}",
                    stockCode, e.getErrorCode());
            return null;
        }
    }

    private BigDecimal parseBigDecimal(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return new BigDecimal(raw.replace(",", "").trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
