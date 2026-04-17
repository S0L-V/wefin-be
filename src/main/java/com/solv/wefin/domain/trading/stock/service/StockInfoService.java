package com.solv.wefin.domain.trading.stock.service;

import com.solv.wefin.domain.trading.dart.dto.DartCompanyInfo;
import com.solv.wefin.domain.trading.dart.dto.DartDividendInfo;
import com.solv.wefin.domain.trading.dart.dto.DartFinancialSummary;
import com.solv.wefin.domain.trading.dart.service.DartCompanyService;
import com.solv.wefin.domain.trading.dart.service.DartDividendService;
import com.solv.wefin.domain.trading.dart.service.DartFinancialService;
import com.solv.wefin.domain.trading.market.dto.StockBasicInfo;
import com.solv.wefin.domain.trading.market.dto.StockIndicatorInfo;
import com.solv.wefin.domain.trading.market.service.StockBasicInfoService;
import com.solv.wefin.domain.trading.market.service.StockIndicatorService;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import com.solv.wefin.web.trading.stock.dto.response.StockInfoResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

/**
 * WEF-645 — GET /api/stocks/{code}/info 통합 조회 서비스.
 * 5개 sub-service 결과를 합쳐 반환. 개별 sub-service 실패는 해당 섹션 null로 graceful degrade.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockInfoService {

    private final StockService stockService;
    private final DartCompanyService dartCompanyService;
    private final DartFinancialService dartFinancialService;
    private final StockBasicInfoService stockBasicInfoService;
    private final StockIndicatorService stockIndicatorService;
    private final DartDividendService dartDividendService;

    public StockInfoResponse getStockInfo(String stockCode) {
        if (!stockService.existsByCode(stockCode)) {
            throw new BusinessException(ErrorCode.MARKET_STOCK_NOT_FOUND);
        }

        DartCompanyInfo company = safeCall(() -> dartCompanyService.getCompany(stockCode), "company", stockCode);
        DartFinancialSummary financial = safeCall(() -> dartFinancialService.getFinancialSummary(stockCode), "financial", stockCode);
        StockBasicInfo basic = safeCall(() -> stockBasicInfoService.getBasicInfo(stockCode), "basic", stockCode);
        StockIndicatorInfo indicator = safeCall(() -> stockIndicatorService.getIndicator(stockCode), "indicator", stockCode);
        DartDividendInfo dividend = safeCall(() -> dartDividendService.getDividend(stockCode), "dividend", stockCode);

        return new StockInfoResponse(company, financial, basic, indicator, dividend);
    }

    /**
     * Sub-service 호출을 방어적으로 감쌈.
     * BusinessException은 로그 후 null로 변환 (부분 실패 허용).
     * 그 외 예외는 전체 장애 가능성이라 상위로 전파.
     */
    private <T> T safeCall(Supplier<T> supplier, String section, String stockCode) {
        try {
            return supplier.get();
        } catch (BusinessException e) {
            log.warn("StockInfo 섹션 조회 실패 (null로 대체): section={}, stockCode={}, code={}",
                    section, stockCode, e.getErrorCode());
            return null;
        }
    }
}
