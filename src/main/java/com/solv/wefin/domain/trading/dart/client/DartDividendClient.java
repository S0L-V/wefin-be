package com.solv.wefin.domain.trading.dart.client;

import com.solv.wefin.domain.trading.dart.client.dto.DartDividendApiResponse;
import com.solv.wefin.domain.trading.dart.config.DartProperties;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class DartDividendClient {

    private static final String DIVIDEND_PATH = "/api/alotMatter.json";

    private final RestClient dartRestClient;
    private final DartProperties dartProperties;

    public DartDividendClient(@Qualifier("dartRestClient") RestClient dartRestClient,
                              DartProperties dartProperties) {
        this.dartRestClient = dartRestClient;
        this.dartProperties = dartProperties;
    }

    public DartDividendApiResponse fetch(String corpCode, String businessYear, String reportCode) {
        DartDividendApiResponse body;
        try {
            body = dartRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(DIVIDEND_PATH)
                            .queryParam("crtfc_key", dartProperties.getKey())
                            .queryParam("corp_code", corpCode)
                            .queryParam("bsns_year", businessYear)
                            .queryParam("reprt_code", reportCode)
                            .build())
                    .retrieve()
                    .body(DartDividendApiResponse.class);
        } catch (Exception e) {
            log.error("DART alotMatter.json 호출 실패: type={}", e.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.DART_DIVIDEND_FETCH_FAILED);
        }
        if (body == null) {
            log.error("DART alotMatter.json 응답 body가 null");
            throw new BusinessException(ErrorCode.DART_DIVIDEND_FETCH_FAILED);
        }
        return body;
    }
}
