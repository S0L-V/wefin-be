package com.solv.wefin.domain.trading.dart.service;

import com.solv.wefin.domain.trading.dart.client.DartFinancialClient;
import com.solv.wefin.domain.trading.dart.client.dto.DartFinancialApiResponse;
import com.solv.wefin.domain.trading.dart.client.dto.DartFinancialItem;
import com.solv.wefin.domain.trading.dart.dto.DartFinancialPeriod;
import com.solv.wefin.domain.trading.dart.dto.DartFinancialSummary;
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
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DartFinancialService {

    private static final String DEFAULT_REPORT_CODE = "11011"; // 사업보고서
    private static final String DEFAULT_FS_DIV = "CFS";        // 연결재무제표
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private static final String ACCOUNT_ASSETS = "ifrs-full_Assets";
    private static final String ACCOUNT_LIABILITIES = "ifrs-full_Liabilities";
    private static final String ACCOUNT_EQUITY = "ifrs-full_Equity";
    private static final String ACCOUNT_REVENUE = "ifrs-full_Revenue";
    private static final String ACCOUNT_OPERATING_INCOME = "dart_OperatingIncomeLoss";
    private static final String ACCOUNT_NET_INCOME = "ifrs-full_ProfitLoss";

    private final DartCorpCodeService dartCorpCodeService;
    private final DartFinancialClient dartFinancialClient;

    @Cacheable(cacheNames = "dartFinancial", key = "#stockCode")
    public DartFinancialSummary getFinancialSummary(String stockCode) {
        String corpCode = dartCorpCodeService.getCorpCode(stockCode);

        int currentYear = LocalDate.now(KST).getYear();
        YearlyResponse yearly = fetchWithYearFallback(corpCode, currentYear);

        return buildSummary(yearly.response(), yearly.businessYear());
    }

    private YearlyResponse fetchWithYearFallback(String corpCode, int currentYear) {
        for (int yearOffset = 1; yearOffset <= 2; yearOffset++) {
            String year = String.valueOf(currentYear - yearOffset);
            DartFinancialApiResponse response =
                    dartFinancialClient.fetch(corpCode, year, DEFAULT_REPORT_CODE, DEFAULT_FS_DIV);

            if (response == null) {
                throw new BusinessException(ErrorCode.DART_FINANCIAL_FETCH_FAILED);
            }
            if (response.isSuccess()) {
                log.debug("DART 재무제표 조회 성공: corp_code={}, year={}", corpCode, year);
                return new YearlyResponse(response, year);
            }
            if (!response.isNoData()) {
                log.error("DART 재무제표 에러 응답: status={}, message={}",
                        response.status(), response.message());
                throw new BusinessException(ErrorCode.DART_FINANCIAL_FETCH_FAILED);
            }
            log.debug("DART 재무제표 미존재, 연도 fallback: corp_code={}, year={}", corpCode, year);
        }
        throw new BusinessException(ErrorCode.DART_FINANCIAL_NOT_FOUND);
    }

    private record YearlyResponse(DartFinancialApiResponse response, String businessYear) {
    }

    private DartFinancialSummary buildSummary(DartFinancialApiResponse response, String businessYear) {
        List<DartFinancialItem> items = response.list();
        if (items == null || items.isEmpty()) {
            throw new BusinessException(ErrorCode.DART_FINANCIAL_NOT_FOUND);
        }

        Map<String, DartFinancialItem> byAccountId = items.stream()
                .filter(item -> item.accountId() != null)
                .collect(java.util.stream.Collectors.toMap(
                        DartFinancialItem::accountId,
                        item -> item,
                        (existing, duplicate) -> existing
                ));

        DartFinancialItem assets = byAccountId.get(ACCOUNT_ASSETS);
        DartFinancialItem liabilities = byAccountId.get(ACCOUNT_LIABILITIES);
        DartFinancialItem equity = byAccountId.get(ACCOUNT_EQUITY);
        DartFinancialItem revenue = byAccountId.get(ACCOUNT_REVENUE);
        DartFinancialItem operatingIncome = byAccountId.get(ACCOUNT_OPERATING_INCOME);
        DartFinancialItem netIncome = byAccountId.get(ACCOUNT_NET_INCOME);

        if (java.util.stream.Stream.of(assets, liabilities, equity, revenue, operatingIncome, netIncome)
                .allMatch(java.util.Objects::isNull)) {
            log.error("DART 재무제표 응답에 핵심 account_id 6개 모두 없음 (IFRS 체계 불일치 가능)");
            throw new BusinessException(ErrorCode.DART_FINANCIAL_NOT_FOUND);
        }

        String currency = firstNonNull(
                assets, liabilities, equity, revenue, operatingIncome, netIncome,
                DartFinancialItem::currency, "KRW");

        DartFinancialPeriod current = buildPeriod(
                firstPeriodName(items, DartFinancialItem::currentPeriodName),
                assets, liabilities, equity, revenue, operatingIncome, netIncome,
                DartFinancialItem::currentAmount);
        DartFinancialPeriod previous = buildPeriod(
                firstPeriodName(items, DartFinancialItem::previousPeriodName),
                assets, liabilities, equity, revenue, operatingIncome, netIncome,
                DartFinancialItem::previousAmount);
        DartFinancialPeriod prePrevious = buildPeriod(
                firstPeriodName(items, DartFinancialItem::prePreviousPeriodName),
                assets, liabilities, equity, revenue, operatingIncome, netIncome,
                DartFinancialItem::prePreviousAmount);

        return new DartFinancialSummary(businessYear, DEFAULT_REPORT_CODE, currency,
                current, previous, prePrevious);
    }

    private DartFinancialPeriod buildPeriod(String periodName,
                                        DartFinancialItem assets,
                                        DartFinancialItem liabilities,
                                        DartFinancialItem equity,
                                        DartFinancialItem revenue,
                                        DartFinancialItem operatingIncome,
                                        DartFinancialItem netIncome,
                                        java.util.function.Function<DartFinancialItem, String> amountExtractor) {
        return new DartFinancialPeriod(
                periodName,
                parseAmount(assets, amountExtractor),
                parseAmount(liabilities, amountExtractor),
                parseAmount(equity, amountExtractor),
                parseAmount(revenue, amountExtractor),
                parseAmount(operatingIncome, amountExtractor),
                parseAmount(netIncome, amountExtractor)
        );
    }

    private BigDecimal parseAmount(DartFinancialItem item,
                                   java.util.function.Function<DartFinancialItem, String> extractor) {
        if (item == null) return null;
        String raw = extractor.apply(item);
        if (raw == null || raw.isBlank() || "-".equals(raw.trim())) return null;
        try {
            return new BigDecimal(raw.replace(",", "").trim());
        } catch (NumberFormatException e) {
            log.warn("DART 재무 금액 파싱 실패: account={}, raw={}", item.accountId(), raw);
            return null;
        }
    }

    private String firstPeriodName(List<DartFinancialItem> items,
                                   java.util.function.Function<DartFinancialItem, String> extractor) {
        return items.stream()
                .map(extractor)
                .filter(name -> name != null && !name.isBlank())
                .findFirst()
                .orElse(null);
    }

    private String firstNonNull(DartFinancialItem a, DartFinancialItem b, DartFinancialItem c,
                                DartFinancialItem d, DartFinancialItem e, DartFinancialItem f,
                                java.util.function.Function<DartFinancialItem, String> extractor,
                                String fallback) {
        return java.util.stream.Stream.of(a, b, c, d, e, f)
                .filter(java.util.Objects::nonNull)
                .map(extractor)
                .filter(s -> s != null && !s.isBlank())
                .findFirst()
                .orElse(fallback);
    }
}
