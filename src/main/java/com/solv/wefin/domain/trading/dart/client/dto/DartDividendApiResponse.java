package com.solv.wefin.domain.trading.dart.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DartDividendApiResponse(
        String status,
        String message,
        List<DartDividendItem> list
) {
    public boolean isSuccess() {
        return "000".equals(status);
    }

    public boolean isNoData() {
        return "013".equals(status);
    }
}
