package com.solv.wefin.domain.game.batch.service;

import com.solv.wefin.domain.game.batch.entity.BatchProgress;
import com.solv.wefin.domain.game.batch.entity.BatchType;
import com.solv.wefin.domain.game.batch.repository.BatchProgressRepository;
import com.solv.wefin.domain.game.stock.entity.StockInfo;
import com.solv.wefin.domain.game.stock.repository.StockInfoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StockInitTxService {

    private final StockInfoRepository stockInfoRepository;
    private final BatchProgressRepository batchProgressRepository;

    private static final BatchType BATCH_TYPE = BatchType.DAILY;

    @Transactional
    public boolean saveOneStock(String symbol, String stockName, String market) {
        if (stockInfoRepository.existsById(symbol)) {
            return false;
        }

        StockInfo stockInfo = StockInfo.create(symbol, stockName, market, null);
        stockInfoRepository.saveAndFlush(stockInfo);

        BatchProgress progress = BatchProgress.create(stockInfo, BATCH_TYPE);
        batchProgressRepository.save(progress);

        return true;
    }
}
