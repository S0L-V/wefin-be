package com.solv.wefin.domain.trading.dart.service;

import com.solv.wefin.domain.trading.dart.client.DartCompanyClient;
import com.solv.wefin.domain.trading.dart.client.dto.DartCompanyApiResponse;
import com.solv.wefin.domain.trading.dart.dto.DartCompanyInfo;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DartCompanyService {

    private final DartCorpCodeService dartCorpCodeService;
    private final DartCompanyClient dartCompanyClient;

    @Cacheable(cacheNames = "dartCompany", key = "#stockCode")
    public DartCompanyInfo getCompany(String stockCode) {
        String corpCode = dartCorpCodeService.getCorpCode(stockCode);
        DartCompanyApiResponse response = dartCompanyClient.fetch(corpCode);

        if (response == null) {
            throw new BusinessException(ErrorCode.DART_COMPANY_FETCH_FAILED);
        }
        if (response.isNoData()) {
            throw new BusinessException(ErrorCode.DART_COMPANY_NOT_FOUND);
        }
        if (!response.isSuccess()) {
            log.error("DART company.json 에러 응답: status={}, message={}",
                    response.status(), response.message());
            throw new BusinessException(ErrorCode.DART_COMPANY_FETCH_FAILED);
        }

        return DartCompanyInfo.from(response);
    }
}
