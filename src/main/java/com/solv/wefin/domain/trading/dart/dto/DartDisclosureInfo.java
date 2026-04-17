package com.solv.wefin.domain.trading.dart.dto;

import com.solv.wefin.domain.trading.dart.client.dto.DartDisclosureApiResponse;

import java.util.List;

public record DartDisclosureInfo(
        List<Item> items,
        Integer totalCount
) {
    public record Item(
            String receiptNo,     // 20260413800802
            String reportName,    // "최대주주등소유주식변동신고서"
            String receiptDate,   // "20260413" (YYYYMMDD)
            String filerName,     // "삼성전자"
            String viewerUrl      // DART 뷰어 URL
    ) {
    }

    private static final String DART_VIEWER_URL = "https://dart.fss.or.kr/dsaf001/main.do?rcpNo=";

    public static DartDisclosureInfo from(DartDisclosureApiResponse response) {
        if (response == null || response.list() == null) {
            return new DartDisclosureInfo(List.of(), 0);
        }
        List<Item> items = response.list().stream()
                .map(raw -> new Item(
                        raw.receiptNo(),
                        raw.reportName() == null ? null : raw.reportName().trim(),
                        raw.receiptDate(),
                        raw.filerName(),
                        raw.receiptNo() != null ? DART_VIEWER_URL + raw.receiptNo() : null
                ))
                .toList();
        return new DartDisclosureInfo(items, response.totalCount());
    }
}
