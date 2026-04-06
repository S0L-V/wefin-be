package com.solv.wefin.domain.game.batch.service;

import com.solv.wefin.domain.game.batch.entity.BatchProgress;
import com.solv.wefin.domain.game.batch.entity.BatchType;
import com.solv.wefin.domain.game.batch.repository.BatchProgressRepository;
import com.solv.wefin.domain.game.stock.entity.StockInfo;
import com.solv.wefin.domain.game.stock.repository.StockInfoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockInitService {

    private final StockInfoRepository stockInfoRepository;
    private final BatchProgressRepository batchProgressRepository;

    private static final String CSV_PATH = "data/stocks.csv";
    private static final BatchType BATCH_TYPE = BatchType.DAILY;

    /**
     * CSV 파일을 읽어 stock_info와 batch_progress를 초기화한다.
     * 이미 존재하는 종목은 스킵한다.
     *
     * @return 신규 등록된 종목 수, 스킵된 종목 수
     */
    @Transactional
    public Map<String, Integer> initFromCsv() {
        int created = 0;
        int skipped = 0;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        new ClassPathResource(CSV_PATH).getInputStream(),
                        StandardCharsets.UTF_8))) {

            // 첫 줄(헤더) 스킵
            String header = reader.readLine();
            if (header == null) {
                throw new IllegalStateException("CSV 파일이 비어있습니다: " + CSV_PATH);
            }

            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                String[] parts = line.split(",", 3);
                if (parts.length < 3) {
                    log.warn("[CSV 파싱 스킵] 잘못된 형식: {}", line);
                    continue;
                }

                String symbol = parts[0].trim();
                String stockName = parts[1].trim();
                String market = parts[2].trim();

                // 이미 존재하는 종목이면 스킵
                if (stockInfoRepository.existsById(symbol)) {
                    skipped++;
                    continue;
                }

                // stock_info 저장 후 flush (BatchProgress가 FK 참조하므로 먼저 DB 반영)
                StockInfo stockInfo = StockInfo.create(symbol, stockName, market, null);
                stockInfoRepository.saveAndFlush(stockInfo);

                // batch_progress 초기화 (PENDING)
                BatchProgress progress = BatchProgress.create(stockInfo, BATCH_TYPE);
                batchProgressRepository.save(progress);

                created++;
            }

        } catch (IOException e) {
            throw new RuntimeException("CSV 파일 읽기 실패: " + CSV_PATH, e);
        }

        log.info("[초기화 완료] 신규={}, 스킵={}", created, skipped);
        return Map.of("created", created, "skipped", skipped);
    }
}
