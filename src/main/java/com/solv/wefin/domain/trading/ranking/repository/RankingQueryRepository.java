package com.solv.wefin.domain.trading.ranking.repository;

import java.time.OffsetDateTime;
import java.util.List;

import com.solv.wefin.domain.trading.ranking.dto.DailyRankingRow;

public interface RankingQueryRepository {

	/**
	 * 주어진 시각 범위 [startInclusive, endExclusive) 안에서
	 * 매도 체결의 실현손익을 계좌별로 집계해서 합계 내림차순으로 반환
	 *
	 * 결과 전체가 필요. setMaxResults / LIMIT 금지.
	 * myRank 계산을 위해 11위 이하도 반드시 포함해야 함.
	 * 성능 개선 필요 시 스프린트 5에서 윈도우 함수로 분리
	 */
	List<DailyRankingRow> findDailySellAggregates(OffsetDateTime startInclusive,
												  OffsetDateTime endExclusive);
}
