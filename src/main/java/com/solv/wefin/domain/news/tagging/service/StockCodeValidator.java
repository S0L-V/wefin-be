package com.solv.wefin.domain.news.tagging.service;

import com.solv.wefin.domain.trading.stock.repository.StockRepository;
import com.solv.wefin.domain.trading.stock.repository.StockRepository.StockCodeNameProjection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * AI가 생성한 종목 코드를 실제 마스터 테이블과 대조하여 검증/정규화한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StockCodeValidator {

    private final StockRepository stockRepository;

    /**
     * 현재 저장된 모든 종목의 `code → canonical name` 스냅샷을 반환한다.
     *
     * @return 종목 코드를 key, 정식 종목명을 value로 갖는 불변 Map
     */
    public Map<String, String> loadStockMap() {
        Map<String, String> map = new HashMap<>();
        int skipped = 0;
        for (StockCodeNameProjection row : stockRepository.findAllStockCodeNamePairs()) {
            String code = row.getCode();
            String name = row.getName();
            if (code == null || name == null) {
                skipped++;
                continue;
            }
            map.put(code, name);
        }
        if (skipped > 0) {
            log.warn("종목 마스터 로드 중 null 포함 row 스킵 - count: {}", skipped);
        }
        return Collections.unmodifiableMap(map);
    }
}
