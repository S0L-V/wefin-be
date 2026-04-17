package com.solv.wefin.domain.trading.indices.service;

import com.solv.wefin.domain.trading.indices.client.YahooChartClient;
import com.solv.wefin.domain.trading.indices.dto.ChangeDirection;
import com.solv.wefin.domain.trading.indices.dto.IndexCode;
import com.solv.wefin.domain.trading.indices.dto.IndexQuote;
import com.solv.wefin.domain.trading.indices.dto.MarketStatus;
import com.solv.wefin.domain.trading.indices.dto.SparklineInterval;
import com.solv.wefin.domain.trading.indices.dto.SparklinePoint;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MarketIndicesServiceTest {

    @Mock
    private YahooChartClient yahooChartClient;

    @InjectMocks
    private MarketIndicesService marketIndicesService;

    @Test
    void 지수_4종_전체_정상조회() {
        // given
        for (IndexCode code : IndexCode.values()) {
            given(yahooChartClient.fetchQuote(code, SparklineInterval.ONE_MIN, 30))
                .willReturn(buildQuote(code));
        }

        // when
        List<IndexQuote> result = marketIndicesService.getAllIndices(SparklineInterval.ONE_MIN, 30);

        // then
        assertThat(result).hasSize(4);
        assertThat(result).extracting(IndexQuote::code)
            .containsExactly(IndexCode.KOSPI, IndexCode.KOSDAQ, IndexCode.NASDAQ, IndexCode.SP500);
    }

    @Test
    void 두번째_호출은_캐시히트로_외부_API_호출_안함() {
        // given
        for (IndexCode code : IndexCode.values()) {
            given(yahooChartClient.fetchQuote(code, SparklineInterval.ONE_MIN, 30))
                .willReturn(buildQuote(code));
        }

        // when
        marketIndicesService.getAllIndices(SparklineInterval.ONE_MIN, 30); // 첫 호출
        marketIndicesService.getAllIndices(SparklineInterval.ONE_MIN, 30); // 두 번째 — 캐시 히트

        // then — 각 지수당 1번만 호출
        for (IndexCode code : IndexCode.values()) {
            verify(yahooChartClient, times(1)).fetchQuote(code, SparklineInterval.ONE_MIN, 30);
        }
    }

    @Test
    void 일부_지수_조회_실패해도_성공한_것만_반환() {
        // given — KOSPI 성공, KOSDAQ 실패, NASDAQ 성공, SP500 실패
        given(yahooChartClient.fetchQuote(IndexCode.KOSPI, SparklineInterval.ONE_MIN, 30))
            .willReturn(buildQuote(IndexCode.KOSPI));
        willThrow(new BusinessException(ErrorCode.MARKET_API_FAILED))
            .given(yahooChartClient).fetchQuote(IndexCode.KOSDAQ, SparklineInterval.ONE_MIN, 30);
        given(yahooChartClient.fetchQuote(IndexCode.NASDAQ, SparklineInterval.ONE_MIN, 30))
            .willReturn(buildQuote(IndexCode.NASDAQ));
        willThrow(new BusinessException(ErrorCode.MARKET_API_FAILED))
            .given(yahooChartClient).fetchQuote(IndexCode.SP500, SparklineInterval.ONE_MIN, 30);

        // when
        List<IndexQuote> result = marketIndicesService.getAllIndices(SparklineInterval.ONE_MIN, 30);

        // then
        assertThat(result).hasSize(2);
        assertThat(result).extracting(IndexQuote::code)
            .containsExactly(IndexCode.KOSPI, IndexCode.NASDAQ);
    }

    @Test
    void interval_또는_포인트수_다르면_캐시키_분리되어_별도_호출() {
        // given
        given(yahooChartClient.fetchQuote(IndexCode.KOSPI, SparklineInterval.ONE_MIN, 30))
            .willReturn(buildQuote(IndexCode.KOSPI));
        given(yahooChartClient.fetchQuote(IndexCode.KOSPI, SparklineInterval.FIVE_MIN, 80))
            .willReturn(buildQuote(IndexCode.KOSPI));
        // 나머지 3개 지수는 실패 처리해서 테스트 단순화
        for (IndexCode code : IndexCode.values()) {
            if (code != IndexCode.KOSPI) {
                willThrow(new BusinessException(ErrorCode.MARKET_API_FAILED))
                    .given(yahooChartClient).fetchQuote(code, SparklineInterval.ONE_MIN, 30);
                willThrow(new BusinessException(ErrorCode.MARKET_API_FAILED))
                    .given(yahooChartClient).fetchQuote(code, SparklineInterval.FIVE_MIN, 80);
            }
        }

        // when
        marketIndicesService.getAllIndices(SparklineInterval.ONE_MIN, 30);
        marketIndicesService.getAllIndices(SparklineInterval.FIVE_MIN, 80);

        // then
        verify(yahooChartClient, times(1)).fetchQuote(IndexCode.KOSPI, SparklineInterval.ONE_MIN, 30);
        verify(yahooChartClient, times(1)).fetchQuote(IndexCode.KOSPI, SparklineInterval.FIVE_MIN, 80);
    }

    private IndexQuote buildQuote(IndexCode code) {
        return new IndexQuote(
            code,
            code.getLabel(),
            new BigDecimal("2560.23"),
            new BigDecimal("12.45"),
            new BigDecimal("0.49"),
            ChangeDirection.UP,
            code.isDelayed(),
            MarketStatus.OPEN,
            List.of(
                new SparklinePoint(Instant.ofEpochSecond(1713254400L), new BigDecimal("2548.10")),
                new SparklinePoint(Instant.ofEpochSecond(1713254460L), new BigDecimal("2550.32"))
            )
        );
    }
}
