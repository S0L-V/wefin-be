package com.solv.wefin.web.trading.stock;

import com.solv.wefin.domain.trading.dart.dto.DartCompanyInfo;
import com.solv.wefin.domain.trading.dart.dto.DartDividendInfo;
import com.solv.wefin.domain.trading.dart.dto.DartFinancialPeriod;
import com.solv.wefin.domain.trading.dart.dto.DartFinancialSummary;
import com.solv.wefin.domain.trading.market.dto.StockBasicInfo;
import com.solv.wefin.domain.trading.market.dto.StockIndicatorInfo;
import com.solv.wefin.domain.trading.stock.service.StockInfoService;
import com.solv.wefin.global.config.security.JwtProvider;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import com.solv.wefin.web.trading.stock.dto.response.StockInfoResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StockInfoController.class)
class StockInfoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StockInfoService stockInfoService;

    @MockitoBean
    private JwtProvider jwtProvider;

    private StockInfoResponse sampleResponse() {
        DartCompanyInfo company = new DartCompanyInfo("삼성전자", "SAMSUNG", "삼성전자", "005930",
                "한종희", "경기도 수원시", "www.samsung.com", "",
                "02-2255-0114", "031-200-7538", "264", "19690113", "12");
        DartFinancialPeriod period = new DartFinancialPeriod("제 55 기",
                new BigDecimal("448234000000000"), null, new BigDecimal("356000000000000"),
                null, null, new BigDecimal("15500000000000"));
        DartFinancialSummary financial = new DartFinancialSummary("2024", "11011", "KRW",
                period, null, null);
        StockBasicInfo basic = new StockBasicInfo(4482340L, 5969782550L, new BigDecimal("53.12"));
        StockIndicatorInfo indicator = new StockIndicatorInfo(
                new BigDecimal("15.2"), new BigDecimal("1.8"),
                new BigDecimal("5800"), new BigDecimal("4.35"));
        DartDividendInfo dividend = new DartDividendInfo("2024",
                new BigDecimal("1444"), new BigDecimal("1.8"), new BigDecimal("17.5"));
        return new StockInfoResponse(company, financial, basic, indicator, dividend);
    }

    @Test
    void 종목정보_조회_정상응답() throws Exception {
        // given
        given(stockInfoService.getStockInfo("005930")).willReturn(sampleResponse());

        // when & then
        mockMvc.perform(get("/api/stocks/005930/info")
                        .with(authentication(
                                new UsernamePasswordAuthenticationToken(null, null, List.of())
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.company.corpName").value("삼성전자"))
                .andExpect(jsonPath("$.data.company.stockCode").value("005930"))
                .andExpect(jsonPath("$.data.financial.businessYear").value("2024"))
                .andExpect(jsonPath("$.data.basic.marketCapInHundredMillionKrw").value(4482340))
                .andExpect(jsonPath("$.data.indicator.per").value(15.2))
                .andExpect(jsonPath("$.data.indicator.roe").value(4.35))
                .andExpect(jsonPath("$.data.dividend.dividendPerShare").value(1444));
    }

    @Test
    void 일부_섹션_null_응답() throws Exception {
        // given — company/dividend 만 성공, 나머지 null
        DartCompanyInfo company = new DartCompanyInfo("카카오", null, null, "035720",
                null, null, null, null, null, null, null, null, null);
        DartDividendInfo dividend = new DartDividendInfo("2024",
                new BigDecimal("50"), new BigDecimal("0.1"), new BigDecimal("3.2"));
        StockInfoResponse partial = new StockInfoResponse(company, null, null, null, dividend);
        given(stockInfoService.getStockInfo("035720")).willReturn(partial);

        // when & then
        mockMvc.perform(get("/api/stocks/035720/info")
                        .with(authentication(
                                new UsernamePasswordAuthenticationToken(null, null, List.of())
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.company.corpName").value("카카오"))
                .andExpect(jsonPath("$.data.financial").doesNotExist())
                .andExpect(jsonPath("$.data.basic").doesNotExist())
                .andExpect(jsonPath("$.data.indicator").doesNotExist())
                .andExpect(jsonPath("$.data.dividend.dividendPerShare").value(50));
    }

    @Test
    void 존재하지_않는_종목은_404_MARKET_STOCK_NOT_FOUND() throws Exception {
        // given
        given(stockInfoService.getStockInfo("999999"))
                .willThrow(new BusinessException(ErrorCode.MARKET_STOCK_NOT_FOUND));

        // when & then
        mockMvc.perform(get("/api/stocks/999999/info")
                        .with(authentication(
                                new UsernamePasswordAuthenticationToken(null, null, List.of())
                        )))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MARKET_STOCK_NOT_FOUND"));
    }
}
