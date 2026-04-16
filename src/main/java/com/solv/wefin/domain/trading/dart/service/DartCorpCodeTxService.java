package com.solv.wefin.domain.trading.dart.service;

import com.solv.wefin.domain.trading.dart.client.dto.DartCorpCodeItem;
import com.solv.wefin.domain.trading.dart.entity.DartCorpCode;
import com.solv.wefin.domain.trading.dart.repository.DartCorpCodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DartCorpCodeTxService {

    private final DartCorpCodeRepository dartCorpCodeRepository;

    @Transactional
    public int upsertAll(List<DartCorpCodeItem> items) {
        Map<String, DartCorpCode> existingByCode = dartCorpCodeRepository.findAll().stream()
                .collect(Collectors.toMap(DartCorpCode::getStockCode, Function.identity()));

        int inserted = 0;
        int updated = 0;
        List<DartCorpCode> toInsert = new ArrayList<>();
        for (DartCorpCodeItem item : items) {
            if (item.stockCode() == null || item.stockCode().isBlank()) {
                continue;
            }
            DartCorpCode existing = existingByCode.get(item.stockCode());
            if (existing != null) {
                existing.update(item.corpCode(), item.corpName(), item.modifyDate());
                updated++;
            } else {
                DartCorpCode created = new DartCorpCode(
                        item.stockCode(),
                        item.corpCode(),
                        item.corpName(),
                        item.modifyDate()
                );
                toInsert.add(created);
                existingByCode.put(item.stockCode(), created);
                inserted++;
            }
        }
        dartCorpCodeRepository.saveAll(toInsert);
        log.info("DART corpCode upsert: inserted={}, updated={}", inserted, updated);
        return inserted + updated;
    }
}
