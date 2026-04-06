package com.solv.wefin.domain.game.kis;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Slf4j
@Component
public class KisStockClient {

    private final RestClient kisRestClient;
    private final KisTokenManager kisTokenManager;
    private final KisProperties kisProperties;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final int MAX_RETRIES = 3;

    public KisStockClient(@Qualifier("kisRestClient") RestClient kisRestClient,
                          KisTokenManager kisTokenManager,
                          KisProperties kisProperties) {
        this.kisRestClient = kisRestClient;
        this.kisTokenManager = kisTokenManager;
        this.kisProperties = kisProperties;
    }

    /**
     * 국내 주식 기간별 일봉 시세를 조회한다.
     * 1회 호출당 최대 100일치 반환.
     * 429 응답 시 exponential backoff (2초→4초→8초, 최대 3회).
     *
     * @param stockCode  종목코드 (ex: "005930")
     * @param marketCode 시장코드 ("J"=코스피, "Q"=코스닥)
     * @param startDate  조회 시작일
     * @param endDate    조회 종료일
     * @return 일봉 데이터 응답
     */
    public KisCandleResponse fetchDailyPrice(String stockCode, String marketCode,
                                              LocalDate startDate, LocalDate endDate) {
        long backoffMs = 2000;

        for (int tries = 1; tries <= MAX_RETRIES; tries++) {
            try {
                return kisRestClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .path("/uapi/domestic-stock/v1/quotations/inquire-daily-itemchartprice")
                                .queryParam("FID_COND_MRKT_DIV_CODE", marketCode)
                                .queryParam("FID_INPUT_ISCD", stockCode)
                                .queryParam("FID_INPUT_DATE_1", startDate.format(DATE_FORMAT))
                                .queryParam("FID_INPUT_DATE_2", endDate.format(DATE_FORMAT))
                                .queryParam("FID_PERIOD_DIV_CODE", "D")
                                .queryParam("FID_ORG_ADJ_PRC", "0")
                                .build())
                        .header("authorization", "Bearer " + kisTokenManager.getAccessToken())
                        .header("appkey", kisProperties.getAppKey())
                        .header("appsecret", kisProperties.getAppSecret())
                        .header("tr_id", "FHKST03010100")
                        .header("custtype", "P")
                        .retrieve()
                        .body(KisCandleResponse.class);

            } catch (HttpClientErrorException.TooManyRequests e) {
                log.warn("[429] 종목={}, 재시도 {}/{}, {}ms 대기", stockCode, tries, MAX_RETRIES, backoffMs);
                if (tries == MAX_RETRIES) {
                    throw new RuntimeException("KIS API 429 최대 재시도 초과: " + stockCode, e);
                }
                sleepSilently(backoffMs);
                backoffMs *= 2;
            } catch (ResourceAccessException e) {
                log.warn("[I/O 에러] 종목={}, 재시도 {}/{}, {}ms 대기, 원인={}",
                        stockCode, tries, MAX_RETRIES, backoffMs, e.getMessage());
                if (tries == MAX_RETRIES) {
                    throw new RuntimeException("KIS API I/O 에러 최대 재시도 초과: " + stockCode, e);
                }
                sleepSilently(backoffMs);
                backoffMs *= 2;
            }
        }

        throw new RuntimeException("KIS API 호출 실패: " + stockCode);
    }

    private void sleepSilently(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
