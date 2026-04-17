package com.solv.wefin.domain.trading.market.service;

import com.solv.wefin.domain.trading.market.client.HantuMarketClient;
import com.solv.wefin.domain.trading.market.client.dto.HantuInvestorTrendApiResponse;
import com.solv.wefin.domain.trading.market.dto.InvestorTrendResponse;
import com.solv.wefin.domain.trading.stock.service.StockService;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class InvestorTrendServiceTest {

    @Mock
    private StockService stockService;

    @Mock
    private HantuMarketClient hantuMarketClient;

    @InjectMocks
    private InvestorTrendService investorTrendService;

    private HantuInvestorTrendApiResponse response(HantuInvestorTrendApiResponse.Output... outputs) {
        return new HantuInvestorTrendApiResponse(List.of(outputs));
    }

    @Test
    void 정상조회_일자별_순매수_매핑() {
        // given
        given(stockService.existsByCode("005930")).willReturn(true);
        given(hantuMarketClient.fetchInvestorTrend("005930"))
                .willReturn(response(
                        new HantuInvestorTrendApiResponse.Output(
                                "20260417", "74500", "500", "2",
                                "-120000", "85000", "35000"),
                        new HantuInvestorTrendApiResponse.Output(
                                "20260416", "74000", "1200", "5",
                                "50000", "-30000", "-20000")
                ));

        // when
        InvestorTrendResponse result = investorTrendService.getInvestorTrend("005930");

        // then
        assertThat(result.stockCode()).isEqualTo("005930");
        assertThat(result.items()).hasSize(2);

        InvestorTrendResponse.Item first = result.items().get(0);
        assertThat(first.date()).isEqualTo(LocalDate.of(2026, 4, 17));
        assertThat(first.closePrice()).isEqualTo(74500L);
        assertThat(first.priceChange()).isEqualTo(500L); // sign=2(상승) → +
        assertThat(first.foreignNetBuy()).isEqualTo(85000L);
        assertThat(first.institutionNetBuy()).isEqualTo(35000L);
        assertThat(first.individualNetBuy()).isEqualTo(-120000L);

        InvestorTrendResponse.Item second = result.items().get(1);
        assertThat(second.priceChange()).isEqualTo(-1200L); // sign=5(하락) → -
    }

    @Test
    void 존재하지_않는_종목은_MARKET_STOCK_NOT_FOUND() {
        // given
        given(stockService.existsByCode("999999")).willReturn(false);

        // when & then
        assertThatThrownBy(() -> investorTrendService.getInvestorTrend("999999"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MARKET_STOCK_NOT_FOUND);
    }

    @Test
    void 한투_RestClient_예외시_MARKET_API_FAILED() {
        // given
        given(stockService.existsByCode("005930")).willReturn(true);
        given(hantuMarketClient.fetchInvestorTrend("005930"))
                .willThrow(new RestClientException("network"));

        // when & then
        assertThatThrownBy(() -> investorTrendService.getInvestorTrend("005930"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MARKET_API_FAILED);
    }

    @Test
    void 응답이_null이면_MARKET_API_FAILED() {
        // given
        given(stockService.existsByCode("005930")).willReturn(true);
        given(hantuMarketClient.fetchInvestorTrend("005930")).willReturn(null);

        // when & then
        assertThatThrownBy(() -> investorTrendService.getInvestorTrend("005930"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MARKET_API_FAILED);
    }

    @Test
    void output이_null이면_빈_리스트_반환() {
        // given
        given(stockService.existsByCode("005930")).willReturn(true);
        given(hantuMarketClient.fetchInvestorTrend("005930"))
                .willReturn(new HantuInvestorTrendApiResponse(null));

        // when
        InvestorTrendResponse result = investorTrendService.getInvestorTrend("005930");

        // then
        assertThat(result.items()).isEmpty();
    }

    @Test
    void 전일대비_0이면_부호_무시하고_0() {
        // given
        given(stockService.existsByCode("005930")).willReturn(true);
        given(hantuMarketClient.fetchInvestorTrend("005930"))
                .willReturn(response(new HantuInvestorTrendApiResponse.Output(
                        "20260417", "74500", "0", "3", "0", "0", "0")));

        // when
        InvestorTrendResponse result = investorTrendService.getInvestorTrend("005930");

        // then
        assertThat(result.items().get(0).priceChange()).isZero();
    }
}
