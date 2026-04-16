package com.solv.wefin.domain.trading.dart.dto;

import com.solv.wefin.domain.trading.dart.client.dto.DartCompanyApiResponse;

public record DartCompanyInfo(
        String corpName,
        String corpNameEng,
        String stockName,
        String stockCode,
        String ceoName,
        String address,
        String homepageUrl,
        String irUrl,
        String phoneNo,
        String faxNo,
        String indutyCode,
        String establishedDate,
        String accountingMonth
) {
    public static DartCompanyInfo from(DartCompanyApiResponse response) {
        return new DartCompanyInfo(
                response.corpName(),
                response.corpNameEng(),
                response.stockName(),
                response.stockCode(),
                response.ceoName(),
                response.address(),
                response.homepageUrl(),
                response.irUrl(),
                response.phoneNo(),
                response.faxNo(),
                response.indutyCode(),
                response.establishedDate(),
                response.accountingMonth()
        );
    }
}
