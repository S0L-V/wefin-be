package com.solv.wefin.domain.trading.stock.service;

import com.solv.wefin.domain.trading.dart.dto.DartCompanyInfo;
import com.solv.wefin.domain.trading.dart.dto.DartDividendInfo;
import com.solv.wefin.domain.trading.dart.dto.DartFinancialPeriod;
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
class StockInfoServiceTest {

    @Mock private StockService stockService;
    @Mock private DartCompanyService dartCompanyService;
    @Mock private DartFinancialService dartFinancialService;
    @Mock private StockBasicInfoService stockBasicInfoService;
    @Mock private StockIndicatorService stockIndicatorService;
    @Mock private DartDividendService dartDividendService;

    @InjectMocks
    private StockInfoService stockInfoService;

    private DartCompanyInfo sampleCompany() {
        return new DartCompanyInfo("삼성전자", "SAMSUNG ELECTRONICS", "삼성전자", "005930",
                "한종희", "경기도 수원시", "www.samsung.com", "",
                "02-2255-0114", "031-200-7538", "264", "19690113", "12");
    }

    private DartFinancialSummary sampleFinancial() {
        DartFinancialPeriod p = new DartFinancialPeriod("제 55 기",
                new BigDecimal("448234000000000"), null, new BigDecimal("356000000000000"),
                null, null, new BigDecimal("15500000000000"));
        return new DartFinancialSummary("2024", "11011", "KRW", p, null, null);
    }

    private StockBasicInfo sampleBasic() {
        return new StockBasicInfo(4482340L, 5969782550L, new BigDecimal("53.12"));
    }

    private StockIndicatorInfo sampleIndicator() {
        return new StockIndicatorInfo(new BigDecimal("15.2"), new BigDecimal("1.8"),
                new BigDecimal("5800"), new BigDecimal("4.35"));
    }

    private DartDividendInfo sampleDividend() {
        return new DartDividendInfo("2024",
                new BigDecimal("1444"), new BigDecimal("1.8"), new BigDecimal("17.5"));
    }

    @Test
    void 통합조회_전부_성공_5개_섹션_모두_채워짐() {
        // given
        given(stockService.existsByCode("005930")).willReturn(true);
        given(dartCompanyService.getCompany("005930")).willReturn(sampleCompany());
        given(dartFinancialService.getFinancialSummary("005930")).willReturn(sampleFinancial());
        given(stockBasicInfoService.getBasicInfo("005930")).willReturn(sampleBasic());
        given(stockIndicatorService.getIndicator("005930")).willReturn(sampleIndicator());
        given(dartDividendService.getDividend("005930")).willReturn(sampleDividend());

        // when
        StockInfoResponse result = stockInfoService.getStockInfo("005930");

        // then
        assertThat(result.company()).isNotNull();
        assertThat(result.company().corpName()).isEqualTo("삼성전자");
        assertThat(result.financial()).isNotNull();
        assertThat(result.basic()).isNotNull();
        assertThat(result.indicator()).isNotNull();
        assertThat(result.dividend()).isNotNull();
    }

    @Test
    void 종목_미존재시_MARKET_STOCK_NOT_FOUND() {
        // given
        given(stockService.existsByCode("999999")).willReturn(false);

        // when & then
        assertThatThrownBy(() -> stockInfoService.getStockInfo("999999"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MARKET_STOCK_NOT_FOUND);
    }

    @Test
    void DART_기업개요_실패시_company_null_나머지_정상() {
        // given
        given(stockService.existsByCode("005930")).willReturn(true);
        given(dartCompanyService.getCompany("005930"))
                .willThrow(new BusinessException(ErrorCode.DART_COMPANY_NOT_FOUND));
        given(dartFinancialService.getFinancialSummary("005930")).willReturn(sampleFinancial());
        given(stockBasicInfoService.getBasicInfo("005930")).willReturn(sampleBasic());
        given(stockIndicatorService.getIndicator("005930")).willReturn(sampleIndicator());
        given(dartDividendService.getDividend("005930")).willReturn(sampleDividend());

        // when
        StockInfoResponse result = stockInfoService.getStockInfo("005930");

        // then
        assertThat(result.company()).isNull();
        assertThat(result.financial()).isNotNull();
        assertThat(result.basic()).isNotNull();
        assertThat(result.indicator()).isNotNull();
        assertThat(result.dividend()).isNotNull();
    }

    @Test
    void 여러_섹션_실패해도_성공한_섹션만_반환() {
        // given
        given(stockService.existsByCode("005930")).willReturn(true);
        given(dartCompanyService.getCompany("005930")).willReturn(sampleCompany());
        given(dartFinancialService.getFinancialSummary("005930"))
                .willThrow(new BusinessException(ErrorCode.DART_FINANCIAL_NOT_FOUND));
        given(stockBasicInfoService.getBasicInfo("005930"))
                .willThrow(new BusinessException(ErrorCode.MARKET_API_FAILED));
        given(stockIndicatorService.getIndicator("005930")).willReturn(sampleIndicator());
        given(dartDividendService.getDividend("005930"))
                .willThrow(new BusinessException(ErrorCode.DART_DIVIDEND_NOT_FOUND));

        // when
        StockInfoResponse result = stockInfoService.getStockInfo("005930");

        // then
        assertThat(result.company()).isNotNull();
        assertThat(result.financial()).isNull();
        assertThat(result.basic()).isNull();
        assertThat(result.indicator()).isNotNull();
        assertThat(result.dividend()).isNull();
    }

    @Test
    void 모든_sub_service_실패시_응답은_모두_null_이지만_200_유지() {
        // given
        given(stockService.existsByCode("005930")).willReturn(true);
        given(dartCompanyService.getCompany("005930"))
                .willThrow(new BusinessException(ErrorCode.DART_COMPANY_FETCH_FAILED));
        given(dartFinancialService.getFinancialSummary("005930"))
                .willThrow(new BusinessException(ErrorCode.DART_FINANCIAL_FETCH_FAILED));
        given(stockBasicInfoService.getBasicInfo("005930"))
                .willThrow(new BusinessException(ErrorCode.MARKET_API_FAILED));
        given(stockIndicatorService.getIndicator("005930"))
                .willThrow(new BusinessException(ErrorCode.MARKET_API_FAILED));
        given(dartDividendService.getDividend("005930"))
                .willThrow(new BusinessException(ErrorCode.DART_DIVIDEND_FETCH_FAILED));

        // when
        StockInfoResponse result = stockInfoService.getStockInfo("005930");

        // then — 전부 null이지만 응답 객체 자체는 반환
        assertThat(result).isNotNull();
        assertThat(result.company()).isNull();
        assertThat(result.financial()).isNull();
        assertThat(result.basic()).isNull();
        assertThat(result.indicator()).isNull();
        assertThat(result.dividend()).isNull();
    }

    @Test
    void BusinessException_외_예외는_상위_전파() {
        // given
        given(stockService.existsByCode("005930")).willReturn(true);
        given(dartCompanyService.getCompany("005930"))
                .willThrow(new RuntimeException("unexpected"));

        // when & then — safeCall이 BusinessException만 잡으므로 RuntimeException은 전파
        assertThatThrownBy(() -> stockInfoService.getStockInfo("005930"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("unexpected");
    }
}
