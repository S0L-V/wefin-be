package com.solv.wefin.domain.market.service;

import com.solv.wefin.domain.market.collector.MarketDataCollector;
import com.solv.wefin.domain.market.dto.CollectedMarketData;
import com.solv.wefin.domain.market.entity.MarketSnapshot;
import com.solv.wefin.domain.market.repository.MarketSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 시장 지표 수집 전체 흐름을 관리하는 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketSnapshotService {

    private final List<MarketDataCollector> collectors;
    private final MarketSnapshotPersistenceService persistenceService;
    private final MarketSnapshotRepository marketSnapshotRepository;

    /**
     * 저장된 모든 시장 지표 스냅샷을 조회한다.
     *
     * @return 전체 스냅샷 목록
     */
    @Transactional(readOnly = true)
    public List<MarketSnapshot> getAllSnapshots() {
        return marketSnapshotRepository.findAll();
    }

    /**
     * 모든 수집기에서 데이터를 수집하고 DB에 upsert한다.
     *
     * <p>외부 API 호출은 트랜잭션 밖에서 수행하고,
     * DB 저장은 별도 서비스(MarketSnapshotPersistenceService)에서 트랜잭션으로 처리한다.</p>
     */
    public void collectAndSave() {
        List<CollectedMarketData> allData = collectFromAllSources();

        if (allData.isEmpty()) {
            log.warn("수집된 시장 지표가 없습니다");
            return;
        }

        persistenceService.saveSnapshots(allData);
    }

    private List<CollectedMarketData> collectFromAllSources() {
        List<CollectedMarketData> allData = new ArrayList<>();

        for (MarketDataCollector collector : collectors) {
            try {
                List<CollectedMarketData> collected = collector.collect();
                allData.addAll(collected);
                log.info("{} 수집 완료: {}건", collector.getSourceName(), collected.size());
            } catch (Exception e) {
                log.error("{} 수집 실패: {}", collector.getSourceName(), e.getMessage(), e);
            }
        }

        return allData;
    }
}
