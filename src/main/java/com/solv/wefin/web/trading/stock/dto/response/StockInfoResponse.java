package com.solv.wefin.web.trading.stock.dto.response;

import com.solv.wefin.domain.trading.dart.dto.DartCompanyInfo;
import com.solv.wefin.domain.trading.dart.dto.DartDividendInfo;
import com.solv.wefin.domain.trading.dart.dto.DartFinancialSummary;
import com.solv.wefin.domain.trading.market.dto.StockBasicInfo;
import com.solv.wefin.domain.trading.market.dto.StockIndicatorInfo;

/**
 * GET /api/stocks/{code}/info — 종목정보 탭 통합 응답.
 * 개별 섹션이 실패하면 해당 필드는 null로 반환 (부분 실패 허용).
 */
public record StockInfoResponse(
        DartCompanyInfo company,
        DartFinancialSummary financial,
        StockBasicInfo basic,
        StockIndicatorInfo indicator,
        DartDividendInfo dividend
) {
}
