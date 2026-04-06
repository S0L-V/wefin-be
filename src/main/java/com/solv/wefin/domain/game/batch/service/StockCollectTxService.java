package com.solv.wefin.domain.game.batch.service;

import com.solv.wefin.domain.game.batch.entity.BatchProgress;
import com.solv.wefin.domain.game.batch.entity.BatchStatus;
import com.solv.wefin.domain.game.batch.repository.BatchProgressRepository;
import com.solv.wefin.domain.game.kis.KisCandleResponse;
import com.solv.wefin.domain.game.stock.entity.StockDaily;
import com.solv.wefin.domain.game.stock.entity.StockInfo;
import com.solv.wefin.domain.game.stock.repository.StockDailyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

/**
 * StockCollectService에서 @Transactional이 필요한 메서드를 분리한 서비스.
 * 동일 클래스 내부 호출은 Spring AOP 프록시를 타지 않아 @Transactional이 무시되므로,
 * 트랜잭션이 필요한 DB 조작은 이 클래스로 위임한다.
 */
@Service
@RequiredArgsConstructor
public class StockCollectTxService {

    private final StockDailyRepository stockDailyRepository;
    private final BatchProgressRepository batchProgressRepository;

    private static final DateTimeFormatter KIS_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Transactional
    public int saveChunk(StockInfo stockInfo, List<KisCandleResponse.Output2> candles) {
        List<LocalDate> allDates = candles.stream()
                .map(c -> LocalDate.parse(c.stck_bsop_date(), KIS_DATE_FORMAT))
                .toList();
        Set<LocalDate> existingDates = stockDailyRepository.findExistingDates(stockInfo, allDates);

        int savedCount = 0;

        for (KisCandleResponse.Output2 candle : candles) {
            LocalDate tradeDate = LocalDate.parse(candle.stck_bsop_date(), KIS_DATE_FORMAT);

            if (existingDates.contains(tradeDate)) {
                continue;
            }

            BigDecimal changeRate = parseBigDecimalOrNull(candle.prdy_ctrt());

            StockDaily daily = StockDaily.create(
                    stockInfo,
                    tradeDate,
                    new BigDecimal(candle.stck_oprc()),
                    new BigDecimal(candle.stck_hgpr()),
                    new BigDecimal(candle.stck_lwpr()),
                    new BigDecimal(candle.stck_clpr()),
                    new BigDecimal(candle.acml_vol()),
                    changeRate
            );

            stockDailyRepository.save(daily);
            savedCount++;
        }

        return savedCount;
    }

    @Transactional
    public void updateStatus(BatchProgress progress, BatchStatus status) {
        if (status == BatchStatus.IN_PROGRESS) {
            progress.startProgress();
        }
        batchProgressRepository.save(progress);
    }

    @Transactional
    public void updateLastCollectedDate(BatchProgress progress, LocalDate lastDate) {
        progress.updateLastCollectedDate(lastDate);
        batchProgressRepository.save(progress);
    }

    @Transactional
    public void completeProgress(BatchProgress progress, LocalDate lastDate) {
        progress.complete(lastDate);
        batchProgressRepository.save(progress);
    }

    @Transactional
    public void failProgress(BatchProgress progress, String errorMessage) {
        progress.fail(errorMessage);
        batchProgressRepository.save(progress);
    }

    private BigDecimal parseBigDecimalOrNull(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
