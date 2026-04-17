package com.solv.wefin.domain.trading.indices.dto;

import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

/**
 * 스파크라인 분봉 간격. Yahoo chart API 가 지원하는 값만 허용한다.
 * <p>Yahoo 는 10m 을 지원하지 않으므로 1m / 5m / 15m 중 선택.</p>
 */
@Getter
@RequiredArgsConstructor
public enum SparklineInterval {
    ONE_MIN("1m", 60L),
    FIVE_MIN("5m", 300L),
    FIFTEEN_MIN("15m", 900L);

    private final String label;
    private final long intervalSeconds;

    public static SparklineInterval fromLabel(String label) {
        if (label == null || label.isBlank()) {
            return ONE_MIN;
        }
        return Arrays.stream(values())
            .filter(v -> v.label.equalsIgnoreCase(label))
            .findFirst()
            .orElseThrow(() -> new BusinessException(ErrorCode.MARKET_INVALID_INTERVAL));
    }
}
