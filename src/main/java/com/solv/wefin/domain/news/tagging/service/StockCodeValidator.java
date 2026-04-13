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
 *
 * 수명 주기: 이 클래스는 상태(캐시)를 보유하지 않는다. @Component 싱글톤이지만
 * 내부 필드 캐싱이나 @PostConstruct 로드를 사용하지 않으므로, 호출자가
 * {@link #loadStockMap()}을 부를 때마다 DB에서 최신 스냅샷을 새로 생성한다.
 * 이 스냅샷의 유효 범위는 "호출자의 배치 1회(예: TaggingService#tagPendingArticles)"로
 * 한정되며, 다음 배치에서는 다시 로드된다. 배치 실행 중 마스터가 갱신되어도
 * 이미 로드된 스냅샷은 갱신되지 않는다
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StockCodeValidator {

    private final StockRepository stockRepository;

    /**
     * 현재 저장된 모든 종목의 `code → canonical name` 스냅샷을 반환한다.
     *
     * 호출 시점마다 DB를 새로 조회하여 불변 Map을 생성한다. 반환된 Map은
     * 호출자의 배치 범위에서만 의미가 있으며, 장시간 재사용하면 최신 마스터와
     * 어긋날 수 있으므로 배치 단위로 재호출해야 한다
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
