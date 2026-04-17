package com.solv.wefin.domain.trading.stock.news.service;

import com.solv.wefin.domain.trading.stock.news.client.WefinNewsClient;
import com.solv.wefin.domain.trading.stock.news.client.dto.WefinNewsApiResponse;
import com.solv.wefin.domain.trading.stock.news.dto.StockNewsInfo;
import com.solv.wefin.domain.trading.stock.service.StockService;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class StockNewsServiceTest {

    @Mock
    private StockService stockService;

    @Mock
    private WefinNewsClient wefinNewsClient;

    @InjectMocks
    private StockNewsService stockNewsService;

    private WefinNewsApiResponse successResponse() {
        return new WefinNewsApiResponse(200, null, null,
                new WefinNewsApiResponse.Data(
                        List.of(
                                new WefinNewsApiResponse.ClusterItem(
                                        1186L, "서민금융 재원 확대", "요약",
                                        "https://example.com/thumb.jpg",
                                        "2026-04-15T11:04:00Z", 24,
                                        List.of(
                                                new WefinNewsApiResponse.Source("dailian.co.kr",
                                                        "https://example.com/news/1"))
                                )
                        ),
                        false, null));
    }

    @Test
    void 뉴스_정상조회_items_매핑() {
        // given
        given(stockService.existsByCode("005930")).willReturn(true);
        given(wefinNewsClient.fetchClusters("005930")).willReturn(successResponse());

        // when
        StockNewsInfo result = stockNewsService.getNews("005930");

        // then
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).clusterId()).isEqualTo(1186L);
        assertThat(result.items().get(0).title()).isEqualTo("서민금융 재원 확대");
        assertThat(result.items().get(0).sources()).hasSize(1);
        assertThat(result.hasNext()).isFalse();
    }

    @Test
    void 존재하지_않는_종목은_MARKET_STOCK_NOT_FOUND() {
        // given
        given(stockService.existsByCode("999999")).willReturn(false);

        // when & then
        assertThatThrownBy(() -> stockNewsService.getNews("999999"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MARKET_STOCK_NOT_FOUND);
    }

    @Test
    void 뉴스팀_응답_status가_200이_아니면_FETCH_FAILED() {
        // given
        given(stockService.existsByCode("005930")).willReturn(true);
        given(wefinNewsClient.fetchClusters("005930"))
                .willReturn(new WefinNewsApiResponse(500, "SERVER_ERROR", "internal error", null));

        // when & then
        assertThatThrownBy(() -> stockNewsService.getNews("005930"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.STOCK_NEWS_FETCH_FAILED);
    }

    @Test
    void 뉴스팀_응답_status가_null이면_FETCH_FAILED() {
        // given — 뉴스팀이 status 필드 누락/결측 시 성공 경로로 흘러가지 않도록
        given(stockService.existsByCode("005930")).willReturn(true);
        given(wefinNewsClient.fetchClusters("005930"))
                .willReturn(new WefinNewsApiResponse(null, null, null, null));

        // when & then
        assertThatThrownBy(() -> stockNewsService.getNews("005930"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.STOCK_NEWS_FETCH_FAILED);
    }

    @Test
    void 정상응답이고_data가_null이면_empty_반환() {
        // given — status=200 성공이지만 검색 결과 없음(data=null) 시나리오는 empty로 허용
        given(stockService.existsByCode("005930")).willReturn(true);
        given(wefinNewsClient.fetchClusters("005930"))
                .willReturn(new WefinNewsApiResponse(200, null, null, null));

        // when
        StockNewsInfo result = stockNewsService.getNews("005930");

        // then
        assertThat(result.items()).isEmpty();
        assertThat(result.hasNext()).isFalse();
    }
}
