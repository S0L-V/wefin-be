package com.solv.wefin.domain.trading.dart.client;

import com.solv.wefin.domain.trading.dart.client.dto.DartFinancialApiResponse;
import com.solv.wefin.domain.trading.dart.config.DartProperties;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class DartFinancialClient {

    private static final String FINANCIAL_PATH = "/api/fnlttSinglAcntAll.json";

    private final RestClient dartRestClient;
    private final DartProperties dartProperties;

    public DartFinancialClient(@Qualifier("dartRestClient") RestClient dartRestClient,
                               DartProperties dartProperties) {
        this.dartRestClient = dartRestClient;
        this.dartProperties = dartProperties;
    }

    public DartFinancialApiResponse fetch(String corpCode,
                                          String businessYear,
                                          String reportCode,
                                          String fsDiv) {
        DartFinancialApiResponse body;
        try {
            body = dartRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(FINANCIAL_PATH)
                            .queryParam("crtfc_key", dartProperties.getKey())
                            .queryParam("corp_code", corpCode)
                            .queryParam("bsns_year", businessYear)
                            .queryParam("reprt_code", reportCode)
                            .queryParam("fs_div", fsDiv)
                            .build())
                    .retrieve()
                    .body(DartFinancialApiResponse.class);
        } catch (Exception e) {
            log.error("DART fnlttSinglAcntAll.json 호출 실패: type={}", e.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.DART_FINANCIAL_FETCH_FAILED);
        }
        if (body == null) {
            log.error("DART fnlttSinglAcntAll.json 응답 body가 null");
            throw new BusinessException(ErrorCode.DART_FINANCIAL_FETCH_FAILED);
        }
        return body;
    }
}
