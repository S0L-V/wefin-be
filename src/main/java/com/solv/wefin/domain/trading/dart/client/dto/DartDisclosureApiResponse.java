package com.solv.wefin.domain.trading.dart.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DartDisclosureApiResponse(
        String status,
        String message,
        @JsonProperty("page_no") Integer pageNo,
        @JsonProperty("page_count") Integer pageCount,
        @JsonProperty("total_count") Integer totalCount,
        @JsonProperty("total_page") Integer totalPage,
        List<Item> list
) {
    public boolean isSuccess() {
        return "000".equals(status);
    }

    public boolean isNoData() {
        return "013".equals(status);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(
            @JsonProperty("rcept_no") String receiptNo,
            @JsonProperty("report_nm") String reportName,
            @JsonProperty("rcept_dt") String receiptDate,
            @JsonProperty("flr_nm") String filerName,
            @JsonProperty("corp_cls") String corpCls,
            @JsonProperty("rm") String remark
    ) {
    }
}
