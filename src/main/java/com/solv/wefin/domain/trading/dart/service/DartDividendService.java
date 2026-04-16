package com.solv.wefin.domain.trading.dart.service;

import com.solv.wefin.domain.trading.dart.client.DartDividendClient;
import com.solv.wefin.domain.trading.dart.client.dto.DartDividendApiResponse;
import com.solv.wefin.domain.trading.dart.client.dto.DartDividendItem;
import com.solv.wefin.domain.trading.dart.dto.DartDividendInfo;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DartDividendService {

    private static final String DEFAULT_REPORT_CODE = "11011"; // 사업보고서
    private static final String COMMON_STOCK = "보통주";
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private static final String CATEGORY_DIVIDEND_PER_SHARE = "주당 현금배당금(원)";
    private static final String CATEGORY_YIELD_RATE = "현금배당수익률(%)";
    private static final String CATEGORY_PAYOUT_RATIO = "현금배당성향(%)";

    private final DartCorpCodeService dartCorpCodeService;
    private final DartDividendClient dartDividendClient;

    @Cacheable(cacheNames = "dartDividend", key = "#stockCode")
    public DartDividendInfo getDividend(String stockCode) {
        String corpCode = dartCorpCodeService.getCorpCode(stockCode);

        int currentYear = LocalDate.now(KST).getYear();
        YearlyResponse yearly = fetchWithYearFallback(corpCode, currentYear);

        return buildDividendInfo(yearly.response().list(), yearly.businessYear());
    }

    private YearlyResponse fetchWithYearFallback(String corpCode, int currentYear) {
        for (int yearOffset = 1; yearOffset <= 2; yearOffset++) {
            String year = String.valueOf(currentYear - yearOffset);
            DartDividendApiResponse response =
                    dartDividendClient.fetch(corpCode, year, DEFAULT_REPORT_CODE);

            if (response.isSuccess()) {
                log.debug("DART 배당 조회 성공: corp_code={}, year={}", corpCode, year);
                return new YearlyResponse(response, year);
            }
            if (!response.isNoData()) {
                log.error("DART 배당 에러 응답: status={}, message={}",
                        response.status(), response.message());
                throw new BusinessException(ErrorCode.DART_DIVIDEND_FETCH_FAILED);
            }
            log.debug("DART 배당 미존재, 연도 fallback: corp_code={}, year={}", corpCode, year);
        }
        throw new BusinessException(ErrorCode.DART_DIVIDEND_NOT_FOUND);
    }

    private DartDividendInfo buildDividendInfo(List<DartDividendItem> items, String businessYear) {
        if (items == null || items.isEmpty()) {
            throw new BusinessException(ErrorCode.DART_DIVIDEND_NOT_FOUND);
        }

        BigDecimal dividendPerShare = pickCurrent(items, CATEGORY_DIVIDEND_PER_SHARE);
        BigDecimal yieldRate = pickCurrent(items, CATEGORY_YIELD_RATE);
        BigDecimal payoutRatio = pickCurrent(items, CATEGORY_PAYOUT_RATIO);

        if (dividendPerShare == null && yieldRate == null && payoutRatio == null) {
            log.error("DART 배당 응답에 핵심 항목 3개 모두 없음 (보통주 기준)");
            throw new BusinessException(ErrorCode.DART_DIVIDEND_NOT_FOUND);
        }

        return new DartDividendInfo(businessYear, dividendPerShare, yieldRate, payoutRatio);
    }

    private BigDecimal pickCurrent(List<DartDividendItem> items, String category) {
        Optional<DartDividendItem> item = items.stream()
                .filter(i -> category.equals(i.category()))
                .filter(i -> COMMON_STOCK.equals(i.stockKind()))
                .findFirst();
        return item.map(i -> parseAmount(i.currentAmount())).orElse(null);
    }

    private BigDecimal parseAmount(String raw) {
        if (raw == null || raw.isBlank() || "-".equals(raw.trim())) return null;
        try {
            return new BigDecimal(raw.replace(",", "").trim());
        } catch (NumberFormatException e) {
            log.warn("DART 배당 금액 파싱 실패: raw={}", raw);
            return null;
        }
    }

    private record YearlyResponse(DartDividendApiResponse response, String businessYear) {
    }
}
