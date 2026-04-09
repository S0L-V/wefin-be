package com.solv.wefin.domain.trading.market.client;

import com.solv.wefin.domain.trading.market.client.dto.HantuCandleApiResponse;
import com.solv.wefin.domain.trading.market.client.dto.HantuOrderbookApiResponse;
import com.solv.wefin.domain.trading.market.client.dto.HantuPriceApiResponse;
import com.solv.wefin.domain.trading.market.client.dto.HantuRecentTradeApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 한투 Open API 시세 조회 클라이언트
 */
@RequiredArgsConstructor
@Component
public class HantuMarketClient {

    private final RestClient hantuRestClient;          // API 호출용
    private final HantuTokenManager hantuTokenManager; // 토큰 가져오기용

    @Value("${hantu.api.appkey}")
    private String appKey;

    @Value("${hantu.api.appsecret}")
    private String appSecret;

    /**
     * 국내 주식 현재가 시세를 조회합니다.
     * @param stockCode 종목코드 (ex: "005930")
     * @return 현재가 시세 응답
     */
    public HantuPriceApiResponse fetchCurrentPrice(String stockCode) {
        return hantuRestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/uapi/domestic-stock/v1/quotations/inquire-price")
                        .queryParam("FID_COND_MRKT_DIV_CODE", "J")
                        .queryParam("FID_INPUT_ISCD", stockCode)
                        .build())
                .header("authorization", "Bearer " + hantuTokenManager.getAccessToken())
                .header("appkey", appKey)
                .header("appsecret", appSecret)
                .header("tr_id", "FHKST01010100")
                .header("custtype", "P")
                .retrieve().body(HantuPriceApiResponse.class);
    }

    /**
     * 국내 주식 호가를 조회합니다.
     * @param stockCode 종목코드
     * @return 호가 응답
     */
    public HantuOrderbookApiResponse fetchOrderbook(String stockCode) {
        return hantuRestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/uapi/domestic-stock/v1/quotations/inquire-asking-price-exp-ccn")
                        .queryParam("FID_COND_MRKT_DIV_CODE", "J")
                        .queryParam("FID_INPUT_ISCD", stockCode)
                        .build())
                .header("authorization", "Bearer " + hantuTokenManager.getAccessToken())
                .header("appkey", appKey)
                .header("appsecret", appSecret)
                .header("tr_id", "FHKST01010200")
                .header("custtype", "P")
                .retrieve().body(HantuOrderbookApiResponse.class);
    }

    /**
     * 국내 주식 기간별 시세를 조회합니다.
     * @param stockCode 종목코드
     * @param start 조회 시작일자
     * @param end 조회 종료일자
     * @param periodCode 기간 분류 코드 (D:일봉, W:주봉, M:월봉, Y:년봉)
     * @return 기간별 시세 응답
     */
    public HantuCandleApiResponse fetchPeriodPrice(String stockCode, LocalDate start, LocalDate end, String periodCode) {
        return hantuRestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/uapi/domestic-stock/v1/quotations/inquire-daily-itemchartprice")
                        .queryParam("FID_COND_MRKT_DIV_CODE", "J")
                        .queryParam("FID_INPUT_ISCD", stockCode)
                        .queryParam("FID_INPUT_DATE_1", start.format(DateTimeFormatter.ofPattern("yyyyMMdd")))
                        .queryParam("FID_INPUT_DATE_2", end.format(DateTimeFormatter.ofPattern("yyyyMMdd")))
                        .queryParam("FID_PERIOD_DIV_CODE", periodCode)
                        .queryParam("FID_ORG_ADJ_PRC", "0")
                        .build())
                .header("authorization", "Bearer " + hantuTokenManager.getAccessToken())
                .header("appkey", appKey)
                .header("appsecret", appSecret)
                .header("tr_id", "FHKST03010100")
                .header("custtype", "P")
                .retrieve().body(HantuCandleApiResponse.class);
    }

    public HantuRecentTradeApiResponse fetchRecentTrades(String stockCode) {
        return hantuRestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/uapi/domestic-stock/v1/quotations/inquire-ccnl")
                        .queryParam("FID_COND_MRKT_DIV_CODE", "J")
                        .queryParam("FID_INPUT_ISCD", stockCode)
                        .build())
                .header("authorization", "Bearer " + hantuTokenManager.getAccessToken())
                .header("appkey", appKey)
                .header("appsecret", appSecret)
                .header("tr_id", "FHKST01010300")
                .header("custtype", "P")
                .retrieve().body(HantuRecentTradeApiResponse.class);
    }
}
