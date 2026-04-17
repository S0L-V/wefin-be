package com.solv.wefin.domain.trading.stock.news.client;

import com.solv.wefin.domain.trading.stock.news.client.dto.WefinNewsApiResponse;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
public class WefinNewsClient {

    private static final String CLUSTERS_PATH = "/api/news/clusters";
    private static final String TAG_TYPE = "STOCK";

    private final RestClient wefinNewsRestClient;

    public WefinNewsClient(@Qualifier("wefinNewsRestClient") RestClient wefinNewsRestClient) {
        this.wefinNewsRestClient = wefinNewsRestClient;
    }

    public WefinNewsApiResponse fetchClusters(String stockCode) {
        WefinNewsApiResponse body;
        try {
            body = wefinNewsRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(CLUSTERS_PATH)
                            .queryParam("tagType", TAG_TYPE)
                            .queryParam("tagCodes", stockCode)
                            .build())
                    .retrieve()
                    .body(WefinNewsApiResponse.class);
        } catch (RestClientException e) {
            log.error("뉴스팀 API 호출 실패: type={}", e.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.STOCK_NEWS_FETCH_FAILED);
        }
        if (body == null) {
            log.error("뉴스팀 API 응답 body가 null");
            throw new BusinessException(ErrorCode.STOCK_NEWS_FETCH_FAILED);
        }
        return body;
    }
}
