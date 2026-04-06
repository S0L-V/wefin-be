package com.solv.wefin.domain.game.batch.entity;

import com.solv.wefin.domain.game.stock.entity.StockInfo;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "batch_progress", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"symbol", "batch_type"})
})
public class BatchProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "progress_id")
    private UUID progressId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "symbol", nullable = false)
    private StockInfo stockInfo;

    @Enumerated(EnumType.STRING)
    @Column(name = "batch_type", nullable = false, length = 20)
    private BatchType batchType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BatchStatus status;

    @Column(name = "last_collected_date")
    private LocalDate lastCollectedDate;

    @Column(name = "retry_count")
    private Integer retryCount;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    /** 수집 시작일 하루 전 = "아직 수집 안 함" */
    private static final LocalDate INITIAL_DATE = LocalDate.of(2019, 12, 31);

    public static BatchProgress create(StockInfo stockInfo, BatchType batchType) {
        BatchProgress progress = new BatchProgress();
        progress.stockInfo = stockInfo;
        progress.batchType = batchType;
        progress.status = BatchStatus.PENDING;
        progress.lastCollectedDate = INITIAL_DATE;
        progress.retryCount = 0;
        progress.errorMessage = "";
        progress.updatedAt = OffsetDateTime.now();
        return progress;
    }

    public void startProgress() {
        this.status = BatchStatus.IN_PROGRESS;
        this.updatedAt = OffsetDateTime.now();
    }

    public void complete(LocalDate lastDate) {
        this.status = BatchStatus.DONE;
        this.lastCollectedDate = lastDate;
        this.errorMessage = "";
        this.updatedAt = OffsetDateTime.now();
    }

    public void fail(String errorMessage) {
        this.status = BatchStatus.FAILED;
        this.retryCount = this.retryCount + 1;
        this.errorMessage = errorMessage;
        this.updatedAt = OffsetDateTime.now();
    }

    public void retry() {
        this.status = BatchStatus.PENDING;
        this.updatedAt = OffsetDateTime.now();
    }
}
