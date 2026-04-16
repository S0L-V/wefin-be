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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class StockIndicatorServiceTest {

    @Mock
    private StockService stockService;

    @Mock
    private HantuMarketClient hantuMarketClient;

    @Mock
    private DartFinancialService dartFinancialService;

    @InjectMocks
    private StockIndicatorService stockIndicatorService;

    private HantuStockInfoApiResponse hantuResponse(String per, String pbr, String eps) {
        return new HantuStockInfoApiResponse(new HantuStockInfoApiResponse.Output(
                null, null, null, per, pbr, eps
        ));
    }

    private DartFinancialSummary financialWithCurrent(BigDecimal netIncome, BigDecimal totalEquity) {
        DartFinancialPeriod current = new DartFinancialPeriod(
                "제 55 기",
                null, null, totalEquity,
                null, null, netIncome
        );
        return new DartFinancialSummary("2024", "11011", "KRW",
                current, null, null);
    }

    @Test
    void 투자지표_정상조회_per_pbr_eps_파싱_roe_계산() {
        // given
        given(stockService.existsByCode("005930")).willReturn(true);
        given(hantuMarketClient.fetchStockInfo("005930"))
                .willReturn(hantuResponse("15.2", "1.8", "5800"));
        given(dartFinancialService.getFinancialSummary("005930"))
                .willReturn(financialWithCurrent(
                        new BigDecimal("15500000000000"),  // 당기순이익 15.5조
                        new BigDecimal("356000000000000")  // 자본총계 356조
                ));

        // when
        StockIndicatorInfo result = stockIndicatorService.getIndicator("005930");

        // then
        assertThat(result.per()).isEqualByComparingTo(new BigDecimal("15.2"));
        assertThat(result.pbr()).isEqualByComparingTo(new BigDecimal("1.8"));
        assertThat(result.eps()).isEqualByComparingTo(new BigDecimal("5800"));
        // ROE = 15.5조 / 356조 × 100 ≈ 4.35%
        assertThat(result.roe()).isEqualByComparingTo(new BigDecimal("4.35"));
    }

    @Test
    void 존재하지_않는_종목조회시_MARKET_STOCK_NOT_FOUND() {
        // given
        given(stockService.existsByCode("999999")).willReturn(false);

        // when & then
        assertThatThrownBy(() -> stockIndicatorService.getIndicator("999999"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MARKET_STOCK_NOT_FOUND);
    }

    @Test
    void 한투_API_예외시_MARKET_API_FAILED() {
        // given
        given(stockService.existsByCode("005930")).willReturn(true);
        given(hantuMarketClient.fetchStockInfo("005930"))
                .willThrow(new RuntimeException("network"));

        // when & then
        assertThatThrownBy(() -> stockIndicatorService.getIndicator("005930"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MARKET_API_FAILED);
    }

    @Test
    void DART_재무제표_실패시_ROE는_null_다른_지표는_정상() {
        // given
        given(stockService.existsByCode("005930")).willReturn(true);
        given(hantuMarketClient.fetchStockInfo("005930"))
                .willReturn(hantuResponse("15.2", "1.8", "5800"));
        given(dartFinancialService.getFinancialSummary("005930"))
                .willThrow(new BusinessException(ErrorCode.DART_FINANCIAL_NOT_FOUND));

        // when
        StockIndicatorInfo result = stockIndicatorService.getIndicator("005930");

        // then
        assertThat(result.per()).isEqualByComparingTo(new BigDecimal("15.2"));
        assertThat(result.roe()).isNull();
    }

    @Test
    void 자본총계가_0이면_ROE_null() {
        // given
        given(stockService.existsByCode("005930")).willReturn(true);
        given(hantuMarketClient.fetchStockInfo("005930"))
                .willReturn(hantuResponse("15.2", "1.8", "5800"));
        given(dartFinancialService.getFinancialSummary("005930"))
                .willReturn(financialWithCurrent(
                        new BigDecimal("1000000"),
                        BigDecimal.ZERO  // 0 division 방지
                ));

        // when
        StockIndicatorInfo result = stockIndicatorService.getIndicator("005930");

        // then
        assertThat(result.roe()).isNull();
    }

    @Test
    void 순이익이나_자본총계가_null이면_ROE_null() {
        // given
        given(stockService.existsByCode("005930")).willReturn(true);
        given(hantuMarketClient.fetchStockInfo("005930"))
                .willReturn(hantuResponse("15.2", "1.8", "5800"));
        given(dartFinancialService.getFinancialSummary("005930"))
                .willReturn(financialWithCurrent(null, new BigDecimal("356000000000000")));

        // when
        StockIndicatorInfo result = stockIndicatorService.getIndicator("005930");

        // then
        assertThat(result.roe()).isNull();
    }

    @Test
    void per_pbr_eps_파싱실패시_해당필드_null() {
        // given
        given(stockService.existsByCode("005930")).willReturn(true);
        given(hantuMarketClient.fetchStockInfo("005930"))
                .willReturn(hantuResponse("NOT_A_NUMBER", "", null));
        given(dartFinancialService.getFinancialSummary("005930"))
                .willReturn(financialWithCurrent(
                        new BigDecimal("15500000000000"),
                        new BigDecimal("356000000000000")
                ));

        // when
        StockIndicatorInfo result = stockIndicatorService.getIndicator("005930");

        // then
        assertThat(result.per()).isNull();
        assertThat(result.pbr()).isNull();
        assertThat(result.eps()).isNull();
        assertThat(result.roe()).isNotNull();
    }
}
