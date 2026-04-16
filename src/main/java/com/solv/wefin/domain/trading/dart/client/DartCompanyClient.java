package com.solv.wefin.domain.trading.dart.client;

import com.solv.wefin.domain.trading.dart.client.dto.DartCompanyApiResponse;
import com.solv.wefin.domain.trading.dart.config.DartProperties;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class DartCompanyClient {

    private static final String COMPANY_PATH = "/api/company.json";

    private final RestClient dartRestClient;
    private final DartProperties dartProperties;

    public DartCompanyClient(@Qualifier("dartRestClient") RestClient dartRestClient,
                             DartProperties dartProperties) {
        this.dartRestClient = dartRestClient;
        this.dartProperties = dartProperties;
    }

    public DartCompanyApiResponse fetch(String corpCode) {
        DartCompanyApiResponse body;
        try {
            body = dartRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(COMPANY_PATH)
                            .queryParam("crtfc_key", dartProperties.getKey())
                            .queryParam("corp_code", corpCode)
                            .build())
                    .retrieve()
                    .body(DartCompanyApiResponse.class);
        } catch (Exception e) {
            log.error("DART company.json 호출 실패: type={}", e.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.DART_COMPANY_FETCH_FAILED);
        }
        if (body == null) {
            log.error("DART company.json 응답 body가 null");
            throw new BusinessException(ErrorCode.DART_COMPANY_FETCH_FAILED);
        }
        return body;
    }
}
