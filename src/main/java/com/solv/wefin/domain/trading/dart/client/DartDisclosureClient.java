package com.solv.wefin.domain.trading.dart.client;

import com.solv.wefin.domain.trading.dart.client.dto.DartDisclosureApiResponse;
import com.solv.wefin.domain.trading.dart.config.DartProperties;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class DartDisclosureClient {

    private static final String LIST_PATH = "/api/list.json";

    private final RestClient dartRestClient;
    private final DartProperties dartProperties;

    public DartDisclosureClient(@Qualifier("dartRestClient") RestClient dartRestClient,
                                DartProperties dartProperties) {
        this.dartRestClient = dartRestClient;
        this.dartProperties = dartProperties;
    }

    /**
     * 공시 목록 조회.
     * @param corpCode DART 고유번호
     * @param bgnDe    조회 시작일 (YYYYMMDD)
     * @param endDe    조회 종료일 (YYYYMMDD)
     * @param pageCount 페이지당 건수 (1~100)
     */
    public DartDisclosureApiResponse fetch(String corpCode, String bgnDe, String endDe, int pageCount) {
        DartDisclosureApiResponse body;
        try {
            body = dartRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(LIST_PATH)
                            .queryParam("crtfc_key", dartProperties.getKey())
                            .queryParam("corp_code", corpCode)
                            .queryParam("bgn_de", bgnDe)
                            .queryParam("end_de", endDe)
                            .queryParam("page_count", pageCount)
                            .build())
                    .retrieve()
                    .body(DartDisclosureApiResponse.class);
        } catch (Exception e) {
            log.error("DART list.json 호출 실패: type={}", e.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.DART_DISCLOSURE_FETCH_FAILED);
        }
        if (body == null) {
            log.error("DART list.json 응답 body가 null");
            throw new BusinessException(ErrorCode.DART_DISCLOSURE_FETCH_FAILED);
        }
        return body;
    }
}
