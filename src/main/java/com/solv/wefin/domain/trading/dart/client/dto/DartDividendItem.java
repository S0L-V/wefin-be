package com.solv.wefin.domain.trading.dart.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DART /api/alotMatter.json 응답 list[] 항목.
 * 필요한 필드만 매핑.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DartDividendItem(
        @JsonProperty("se") String category,          // 구분명 (예: "주당 현금배당금(원)", "현금배당수익률(%)")
        @JsonProperty("stock_knd") String stockKind,  // 주식 종류 (보통주/우선주 등)
        @JsonProperty("thstrm") String currentAmount,
        @JsonProperty("frmtrm") String previousAmount,
        @JsonProperty("bfefrmtrm") String prePreviousAmount
) {
}
