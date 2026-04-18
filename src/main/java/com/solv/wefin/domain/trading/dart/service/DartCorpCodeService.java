package com.solv.wefin.domain.trading.dart.service;

import com.solv.wefin.domain.trading.dart.repository.DartCorpCodeRepository;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DartCorpCodeService {

    private final DartCorpCodeRepository dartCorpCodeRepository;

    @Cacheable(cacheNames = "dartCorpCode", key = "#stockCode")
    public String getCorpCode(String stockCode) {
        return dartCorpCodeRepository.findByStockCode(stockCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.DART_CORP_CODE_NOT_FOUND))
                .getCorpCode();
    }
}
