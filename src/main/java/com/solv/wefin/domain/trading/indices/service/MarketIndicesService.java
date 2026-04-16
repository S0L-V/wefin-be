package com.solv.wefin.domain.trading.indices.service;

import com.solv.wefin.domain.trading.indices.client.YahooChartClient;
import com.solv.wefin.domain.trading.indices.dto.IndexCode;
import com.solv.wefin.domain.trading.indices.dto.IndexQuote;
import com.solv.wefin.domain.trading.indices.dto.SparklineInterval;
import com.solv.wefin.global.error.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 주요 지수 4종 조회 서비스.
 *
 * <p>Yahoo Finance chart API 를 on-demand 호출하고, 60초 TTL 인메모리 캐시로 외부 호출을 줄인다.
 * 4개 지수 중 일부 조회 실패 시에도 성공한 지수만 반환한다(부분 성공 허용).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketIndicesService {

    private static final long CACHE_TTL_MS = 60_000L;

    private final YahooChartClient yahooChartClient;

    // cacheKey = "CODE:interval:sparklinePoints" 로 분기
    private final ConcurrentHashMap<String, IndexQuote> quoteCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> quoteCacheTimestamp = new ConcurrentHashMap<>();

    /**
     * 4개 지수 전체를 조회한다.
     *
     * @param interval         분봉 간격
     * @param sparklinePoints  지수별 스파크라인 포인트 개수
     */
    public List<IndexQuote> getAllIndices(SparklineInterval interval, int sparklinePoints) {
        List<IndexQuote> result = new ArrayList<>();
        for (IndexCode code : IndexCode.values()) {
            try {
                result.add(getOne(code, interval, sparklinePoints));
            } catch (BusinessException e) {
                log.warn("지수 {} 조회 실패 — 스킵", code, e);
            }
        }
        return result;
    }

    private IndexQuote getOne(IndexCode code, SparklineInterval interval, int sparklinePoints) {
        String cacheKey = code.name() + ":" + interval.getLabel() + ":" + sparklinePoints;

        IndexQuote cached = getCached(cacheKey);
        if (cached != null) {
            return cached;
        }

        IndexQuote quote = yahooChartClient.fetchQuote(code, interval, sparklinePoints);
        quoteCache.put(cacheKey, quote);
        quoteCacheTimestamp.put(cacheKey, System.currentTimeMillis());
        return quote;
    }

    private IndexQuote getCached(String cacheKey) {
        Long ts = quoteCacheTimestamp.get(cacheKey);
        if (ts != null && System.currentTimeMillis() - ts < CACHE_TTL_MS) {
            return quoteCache.get(cacheKey);
        }
        if (ts != null) {
            quoteCache.remove(cacheKey);
            quoteCacheTimestamp.remove(cacheKey);
        }
        return null;
    }
}
