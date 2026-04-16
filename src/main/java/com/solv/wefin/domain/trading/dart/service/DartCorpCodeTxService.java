package com.solv.wefin.domain.trading.dart.service;

import com.solv.wefin.domain.trading.dart.client.dto.DartCorpCodeItem;
import com.solv.wefin.domain.trading.dart.entity.DartCorpCode;
import com.solv.wefin.domain.trading.dart.repository.DartCorpCodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DartCorpCodeTxService {

    private final DartCorpCodeRepository dartCorpCodeRepository;

    @Transactional
    public int upsertAll(List<DartCorpCodeItem> items) {
        Map<String, DartCorpCode> existingByCode = dartCorpCodeRepository.findAll().stream()
                .collect(Collectors.toMap(DartCorpCode::getStockCode, Function.identity()));

        List<DartCorpCode> toInsert = new ArrayList<>();
        for (DartCorpCodeItem item : items) {
            DartCorpCode existing = existingByCode.get(item.stockCode());
            if (existing != null) {
                existing.update(item.corpCode(), item.corpName(), item.modifyDate());
            } else {
                toInsert.add(new DartCorpCode(
                        item.stockCode(),
                        item.corpCode(),
                        item.corpName(),
                        item.modifyDate()
                ));
            }
        }
        dartCorpCodeRepository.saveAll(toInsert);
        return items.size();
    }
}
