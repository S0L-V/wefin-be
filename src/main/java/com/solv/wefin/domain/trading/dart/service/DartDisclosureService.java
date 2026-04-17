package com.solv.wefin.domain.trading.dart.service;

import com.solv.wefin.domain.trading.dart.client.DartDisclosureClient;
import com.solv.wefin.domain.trading.dart.client.dto.DartDisclosureApiResponse;
import com.solv.wefin.domain.trading.dart.dto.DartDisclosureInfo;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DartDisclosureService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final int LOOKBACK_MONTHS = 3;
    private static final int PAGE_COUNT = 20; // 최근 20건

    private final DartCorpCodeService dartCorpCodeService;
    private final DartDisclosureClient dartDisclosureClient;

    @Cacheable(cacheNames = "dartDisclosure", key = "#stockCode")
    public DartDisclosureInfo getDisclosures(String stockCode) {
        String corpCode = dartCorpCodeService.getCorpCode(stockCode);

        LocalDate today = LocalDate.now(KST);
        String endDe = today.format(DATE_FMT);
        String bgnDe = today.minusMonths(LOOKBACK_MONTHS).format(DATE_FMT);

        DartDisclosureApiResponse response =
                dartDisclosureClient.fetch(corpCode, bgnDe, endDe, PAGE_COUNT);

        if (response.isNoData()) {
            return new DartDisclosureInfo(List.of(), 0);
        }
        if (!response.isSuccess()) {
            log.error("DART 공시 에러 응답: status={}, message={}",
                    response.status(), response.message());
            throw new BusinessException(ErrorCode.DART_DISCLOSURE_FETCH_FAILED);
        }

        return DartDisclosureInfo.from(response);
    }
}
