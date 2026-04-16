package com.solv.wefin.domain.trading.market.service;

import com.solv.wefin.domain.trading.market.client.HantuMarketClient;
import com.solv.wefin.domain.trading.market.client.dto.HantuStockInfoApiResponse;
import com.solv.wefin.domain.trading.market.dto.StockBasicInfo;
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
class StockBasicInfoServiceTest {

    @Mock
    private StockService stockService;

    @Mock
    private HantuMarketClient hantuMarketClient;

    @InjectMocks
    private StockBasicInfoService stockBasicInfoService;

    @Test
    void 기본정보_정상조회_시가총액은_억단위를_원단위로_변환() {
        // given
        given(stockService.existsByCode("005930")).willReturn(true);
        given(hantuMarketClient.fetchStockInfo("005930")).willReturn(
                new HantuStockInfoApiResponse(new HantuStockInfoApiResponse.Output(
                        "4482340",    // 시가총액 448조 2340억 = 448,234,000,000,000원
                        "5969782550", // 상장주식수
                        "53.12",      // 외국인 소진율
                        null, null, null  // per/pbr/eps — 기본정보 테스트 범위 외
                ))
        );

        // when
        StockBasicInfo result = stockBasicInfoService.getBasicInfo("005930");

        // then
        assertThat(result.marketCapInHundredMillionKrw()).isEqualTo(4482340L);
        assertThat(result.listedShares()).isEqualTo(5969782550L);
        assertThat(result.foreignRatio()).isEqualByComparingTo(new BigDecimal("53.12"));
    }

    @Test
    void 존재하지_않는_종목조회시_MARKET_STOCK_NOT_FOUND() {
        // given
        given(stockService.existsByCode("999999")).willReturn(false);

        // when & then
        assertThatThrownBy(() -> stockBasicInfoService.getBasicInfo("999999"))
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
        assertThatThrownBy(() -> stockBasicInfoService.getBasicInfo("005930"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MARKET_API_FAILED);
    }

    @Test
    void 응답이_null이면_MARKET_API_FAILED() {
        // given
        given(stockService.existsByCode("005930")).willReturn(true);
        given(hantuMarketClient.fetchStockInfo("005930")).willReturn(null);

        // when & then
        assertThatThrownBy(() -> stockBasicInfoService.getBasicInfo("005930"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MARKET_API_FAILED);
    }

    @Test
    void output이_null이면_MARKET_API_FAILED() {
        // given
        given(stockService.existsByCode("005930")).willReturn(true);
        given(hantuMarketClient.fetchStockInfo("005930"))
                .willReturn(new HantuStockInfoApiResponse(null));

        // when & then
        assertThatThrownBy(() -> stockBasicInfoService.getBasicInfo("005930"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MARKET_API_FAILED);
    }

    @Test
    void 파싱_실패_필드는_null로_수렴() {
        // given
        given(stockService.existsByCode("005930")).willReturn(true);
        given(hantuMarketClient.fetchStockInfo("005930")).willReturn(
                new HantuStockInfoApiResponse(new HantuStockInfoApiResponse.Output(
                        "NOT_A_NUMBER", "", null,
                        null, null, null
                ))
        );

        // when
        StockBasicInfo result = stockBasicInfoService.getBasicInfo("005930");

        // then
        assertThat(result.marketCapInHundredMillionKrw()).isNull();
        assertThat(result.listedShares()).isNull();
        assertThat(result.foreignRatio()).isNull();
    }
}
