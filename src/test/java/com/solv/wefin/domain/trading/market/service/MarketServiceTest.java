package com.solv.wefin.domain.trading.market.service;

import com.solv.wefin.domain.trading.market.client.HantuMarketClient;
import com.solv.wefin.domain.trading.market.client.dto.HantuPriceApiResponse;
import com.solv.wefin.domain.trading.market.client.dto.HantuRecentTradeApiResponse;
import com.solv.wefin.domain.trading.market.dto.PriceResponse;
import com.solv.wefin.domain.trading.market.dto.RecentTradeResponse;
import com.solv.wefin.domain.trading.stock.service.StockService;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MarketServiceTest {

    @Mock
    private HantuMarketClient hantuMarketClient;

    @Mock
    private StockService stockService;

    @InjectMocks
    private MarketService marketService;


    // === getPrice 테스트 ===

    @Test
    void 현재가_정상조회() {
        // given
        given(stockService.existsByCode("005930")).willReturn(true);
        given(hantuMarketClient.fetchCurrentPrice("005930")).willReturn(
                new HantuPriceApiResponse(new HantuPriceApiResponse.Output(
                        "97500", "1200", "2", "1.25",
                        "12340567", "1200000000000",
                        "96800", "98200", "96300",
                        "126700", "68100",
                        "15.2", "1.8", "580000000"
                ))
        );

        // when
        PriceResponse result = marketService.getPrice("005930");

        // then
        assertThat(result.currentPrice()).isEqualTo(97500);
        assertThat(result.stockCode()).isEqualTo("005930");
    }

    @Test
    void 현재가_캐시히트시_API_호출안함() {
        // given
        given(stockService.existsByCode("005930")).willReturn(true);
        given(hantuMarketClient.fetchCurrentPrice("005930")).willReturn(
                new HantuPriceApiResponse(new HantuPriceApiResponse.Output(
                        "97500", "1200", "2", "1.25",
                        "12340567", "1200000000000",
                        "96800", "98200", "96300",
                        "126700", "68100",
                        "15.2", "1.8", "580000000"
                ))
        );

        // when
        marketService.getPrice("005930"); // 첫 호출 — API 호출
        marketService.getPrice("005930"); // 두 번째 — 캐시 히트

        // then
        verify(hantuMarketClient, times(1)).fetchCurrentPrice("005930");
    }

    @Test
    void 존재하지_않는_종목코드로_현재가_조회시_예외() {
        // given
        given(stockService.existsByCode("999999")).willReturn(false);

        // when & then
        assertThatThrownBy(() -> marketService.getPrice("999999"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MARKET_STOCK_NOT_FOUND);
    }



    // === getCandles 테스트 ===

    @Test
    void 캔들_시작일이_종료일보다_늦으면_예외() {
        // given
        given(stockService.existsByCode("005930")).willReturn(true);

        // when & then
        assertThatThrownBy(() -> marketService.getCandles(
                "005930", LocalDate.of(2026, 4, 10), LocalDate.of(2026, 4, 1), "D"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MARKET_INVALID_DATE);
    }

    @Test
    void 유효하지_않은_기간코드로_캔들_조회시_예외() {
        // given
        given(stockService.existsByCode("005930")).willReturn(true);

        // when & then
        assertThatThrownBy(() -> marketService.getCandles(
                "005930", LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 10), "X"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MARKET_INVALID_PERIOD_CODE);
    }



    // === getRecentTrades 테스트 ===

        @Test
    void 존재하지_않는_종목코드로_최근체결_조회시_예외() {
        given(stockService.existsByCode("999999")).willReturn(false);

        assertThatThrownBy(() -> marketService.getRecentTrades("999999"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MARKET_STOCK_NOT_FOUND);
    }

    @Test
    void 최근체결_정상조회() {
        // given
        given(stockService.existsByCode("005930")).willReturn(true);
        given(hantuMarketClient.fetchRecentTrades("005930")).willReturn(
                new HantuRecentTradeApiResponse(List.of(
                        new HantuRecentTradeApiResponse.Output(
                                "104610", "97500", "1200", "2", "1", "106.78", "1.25"
                        ),
                        new HantuRecentTradeApiResponse.Output(
                                "104611", "97600", "1300", "2", "3", "106.80", "1.32"
                        )
                ))
        );

        // when
        List<RecentTradeResponse> result = marketService.getRecentTrades("005930");

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).price()).isEqualByComparingTo(new BigDecimal("97500"));
        assertThat(result.get(0).tradeStrength()).isEqualTo(106.78f);
        assertThat(result.get(1).tradeTime()).isEqualTo("104611");
    }
}