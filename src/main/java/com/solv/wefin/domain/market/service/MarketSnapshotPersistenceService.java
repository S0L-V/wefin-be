package com.solv.wefin.domain.market.service;

import com.solv.wefin.domain.market.dto.CollectedMarketData;
import com.solv.wefin.domain.market.entity.MarketSnapshot;
import com.solv.wefin.domain.market.repository.MarketSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 시장 지표 스냅샷의 DB 저장을 담당한다.
 *MarketSnapshotService에서 외부 API 호출과 트랜잭션을 분리하기 위해
 * self-invocation 방지 목적으로 별도 서비스로 분리한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketSnapshotPersistenceService {

    private final MarketSnapshotRepository marketSnapshotRepository;

    /**
     * 수집된 시장 지표를 DB에 upsert한다.
     *
     * @param allData 수집된 시장 지표 목록
     */
    @Transactional
    public void saveSnapshots(List<CollectedMarketData> allData) {
        for (CollectedMarketData data : allData) {
            upsertSnapshot(data);
        }
        log.info("시장 지표 upsert 완료: {}건", allData.size());
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
