package com.solv.wefin.domain.trading.market.dto;

import com.solv.wefin.domain.trading.market.client.dto.HantuStockInfoApiResponse;

import java.math.BigDecimal;

public record StockBasicInfo(
        Long marketCapInHundredMillionKrw,  // 시가총액 (단위: 억원, 한투 원본 단위 유지)
        Long listedShares,                  // 상장주식수
        BigDecimal foreignRatio             // 외국인 소진율 (%)
) {

    /**
     * 한투 응답 → 도메인 DTO 매핑.
     * 시가총액은 한투가 제공하는 억원 단위를 그대로 유지 (FE 포맷팅 편의 + 원본 단위 투명성).
     * 파싱 실패/빈 문자열은 null.
     */
    public static StockBasicInfo from(HantuStockInfoApiResponse.Output output) {
        return new StockBasicInfo(
                parseLong(output.hts_avls()),
                parseLong(output.lstn_stcn()),
                parseBigDecimal(output.hts_frgn_ehrt())
        );
    }

    private static BigDecimal parseBigDecimal(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return new BigDecimal(raw.replace(",", "").trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Long parseLong(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return Long.parseLong(raw.replace(",", "").trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
