package com.solv.wefin.domain.game.batch.repository;

import com.solv.wefin.domain.game.batch.entity.BatchProgress;
import com.solv.wefin.domain.game.batch.entity.BatchStatus;
import com.solv.wefin.domain.game.batch.entity.BatchType;
import com.solv.wefin.domain.game.stock.entity.StockInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BatchProgressRepository extends JpaRepository<BatchProgress, UUID> {

    Optional<BatchProgress> findByStockInfoAndBatchType(StockInfo stockInfo, BatchType batchType);

    @Query("SELECT bp FROM BatchProgress bp JOIN FETCH bp.stockInfo WHERE bp.status = :status")
    List<BatchProgress> findByStatus(BatchStatus status);

    List<BatchProgress> findByBatchType(BatchType batchType);

    long countByBatchTypeAndStatus(BatchType batchType, BatchStatus status);

    @Query("SELECT bp.status, COUNT(bp) FROM BatchProgress bp WHERE bp.batchType = :batchType GROUP BY bp.status")
    List<Object[]> countGroupByStatus(BatchType batchType);
}
