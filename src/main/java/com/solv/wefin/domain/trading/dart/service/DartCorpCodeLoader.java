package com.solv.wefin.domain.trading.dart.service;

import com.solv.wefin.domain.trading.dart.client.DartCorpCodeClient;
import com.solv.wefin.domain.trading.dart.client.dto.DartCorpCodeItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DartCorpCodeLoader {

    private static final String CACHE_NAME = "dartCorpCode";

    private final DartCorpCodeClient dartCorpCodeClient;
    private final DartCorpCodeTxService dartCorpCodeTxService;
    private final CacheManager cacheManager;

    public int refresh() {
        List<DartCorpCodeItem> items = dartCorpCodeClient.fetchAll();
        log.info("DART corpCode fetched: {} items (listed only)", items.size());

        int upserted = dartCorpCodeTxService.upsertAll(items);
        evictCache();

        log.info("DART corpCode upserted: {} items", upserted);
        return upserted;
    }

    private void evictCache() {
        Cache cache = cacheManager.getCache(CACHE_NAME);
        if (cache != null) {
            cache.clear();
        }
    }
}
