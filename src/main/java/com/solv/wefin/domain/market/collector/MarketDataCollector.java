package com.solv.wefin.domain.market.collector;

import com.solv.wefin.domain.market.dto.CollectedMarketData;
import com.solv.wefin.domain.market.entity.MarketSnapshot.ChangeDirection;

import java.math.BigDecimal;
import java.util.List;

/**
 * 외부 API에서 시장 지표 데이터를 수집하는 인터페이스
 */
public interface MarketDataCollector {

    /**
     * 외부 API를 호출하여 시장 지표 데이터를 수집한다.
     *
     * @return 수집된 시장 지표 목록
     */
    List<CollectedMarketData> collect();

    /**
     * 데이터 소스 이름을 반환한다.
     *
     * @return 소스 이름 (ex. YahooFinance, BOK)
     */
    String getSourceName();

    /**
     * 변동 값으로 변동 방향을 판단한다.
     *
     * @param changeValue 변동 값
     * @return UP, DOWN, FLAT
     */
    default ChangeDirection resolveDirection(BigDecimal changeValue) {
        int cmp = changeValue.compareTo(BigDecimal.ZERO);
        if (cmp > 0) return ChangeDirection.UP;
        if (cmp < 0) return ChangeDirection.DOWN;
        return ChangeDirection.FLAT;
    }
}
