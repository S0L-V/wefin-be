package com.solv.wefin.domain.trading.snapshot.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(
	name = "daily_snapshot",
	uniqueConstraints = {
		@UniqueConstraint(columnNames = {"virtual_account_id", "snapshot_date"})
	}
)
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailySnapshot {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long dailySnapshotId;

	@Column(nullable = false)
	private Long virtualAccountId;

	@Column(nullable = false)
	private LocalDate snapshotDate;

	@Column(nullable = false)
	private BigDecimal totalAsset;

	@Column(nullable = false)
	private BigDecimal balance;

	@Column(nullable = false)
	private BigDecimal evaluationAmount;

	@Column(nullable = false)
	private BigDecimal realizedProfit;

	@Column(nullable = false)
	@CreatedDate
	private OffsetDateTime createdAt;

	public DailySnapshot(Long virtualAccountId, LocalDate snapshotDate,
						 BigDecimal totalAsset, BigDecimal balance,
						 BigDecimal evaluationAmount, BigDecimal realizedProfit) {
		this.virtualAccountId = virtualAccountId;
		this.snapshotDate = snapshotDate;
		this.totalAsset = totalAsset;
		this.balance = balance;
		this.evaluationAmount = evaluationAmount;
		this.realizedProfit = realizedProfit;
	}

	public static DailySnapshot of(Long virtualAccountId, LocalDate snapshotDate,
								   BigDecimal totalAsset, BigDecimal balance,
								   BigDecimal evaluationAmount, BigDecimal realizedProfit) {
		return new DailySnapshot(virtualAccountId, snapshotDate, totalAsset, balance,
			evaluationAmount, realizedProfit);
	}
}
