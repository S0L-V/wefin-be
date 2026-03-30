package com.solv.wefin.domain.trading.market.client;

import com.solv.wefin.domain.trading.market.dto.HantuOrderbookApiResponse;
import com.solv.wefin.domain.trading.market.dto.HantuPriceApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

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
}
