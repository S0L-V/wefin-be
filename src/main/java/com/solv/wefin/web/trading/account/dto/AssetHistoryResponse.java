package com.solv.wefin.web.trading.account.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.solv.wefin.domain.trading.snapshot.entity.DailySnapshot;

public record AssetHistoryResponse(List<AssetHistoryItem> history) {
	public record AssetHistoryItem(
		LocalDate date,
		BigDecimal totalAsset,
		BigDecimal balance,
		BigDecimal evaluationAmount
	) {}

	public static AssetHistoryResponse from(List<DailySnapshot> snapshots) {
		return new AssetHistoryResponse(snapshots.stream()
			.map(s -> new AssetHistoryItem(
				s.getSnapshotDate(),
				s.getTotalAsset(),
				s.getBalance(),
				s.getEvaluationAmount()
			)).toList());
	}
}
