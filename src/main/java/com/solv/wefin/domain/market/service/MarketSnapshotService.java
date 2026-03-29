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
    private final MarketSnapshotRepository marketSnapshotRepository;

    /**
     * 모든 수집기에서 데이터를 수집하고 DB에 upsert한다.
     */
    @Transactional
    public void collectAndSave() {
        List<CollectedMarketData> allData = collectFromAllSources();

        if (allData.isEmpty()) {
            log.warn("수집된 시장 지표가 없습니다");
            return;
        }

        for (CollectedMarketData data : allData) {
            upsertSnapshot(data);
        }

        log.info("시장 지표 upsert 완료: {}건", allData.size());
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

    private void upsertSnapshot(CollectedMarketData data) {
        MarketSnapshot snapshot = marketSnapshotRepository
                .findByMetricType(data.getMetricType())
                .orElse(null);

        if (snapshot != null) {
            snapshot.updateValues(
                    data.getValue(),
                    data.getChangeRate(),
                    data.getChangeValue(),
                    data.getChangeDirection());
        } else {
            snapshot = MarketSnapshot.builder()
                    .metricType(data.getMetricType())
                    .label(data.getLabel())
                    .value(data.getValue())
                    .changeRate(data.getChangeRate())
                    .changeValue(data.getChangeValue())
                    .unit(data.getUnit())
                    .changeDirection(data.getChangeDirection())
                    .build();
            marketSnapshotRepository.save(snapshot);
        }
    }
}
