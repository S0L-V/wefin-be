package com.solv.wefin.domain.trading.dart.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * DART /api/fnlttSinglAcntAll.json 응답 원본.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DartFinancialApiResponse(
        String status,
        String message,
        List<DartFinancialItem> list
) {
    public boolean isSuccess() {
        return "000".equals(status);
    }

    public boolean isNoData() {
        return "013".equals(status);
    }
}
