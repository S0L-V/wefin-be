package com.solv.wefin.domain.trading.market.service;

import com.solv.wefin.domain.trading.common.ExchangeRateProvider;
import com.solv.wefin.domain.trading.market.client.HantuMarketClient;
import com.solv.wefin.domain.trading.market.client.dto.HantuOrderbookApiResponse;
import com.solv.wefin.domain.trading.market.client.dto.HantuPriceApiResponse;
import com.solv.wefin.domain.trading.market.dto.OrderbookResponse;
import com.solv.wefin.domain.trading.market.dto.PriceResponse;
import com.solv.wefin.domain.trading.common.MarketPriceProvider;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
@Service
public class MarketService implements MarketPriceProvider, ExchangeRateProvider {

    private final HantuMarketClient hantuMarketClient;

    private final ConcurrentHashMap<String, CompletableFuture<PriceResponse>> ongoingRequests = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, PriceResponse> priceCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> priceCacheTimestamp = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 5000; // 5초

    public PriceResponse getPrice(String stockCode) {
        // 캐시 확인
        Long cachedTime = priceCacheTimestamp.get(stockCode);
        if (cachedTime != null && System.currentTimeMillis() - cachedTime < CACHE_TTL_MS) {
            return priceCache.get(stockCode);
        }

        // thundering herd 방지: 같은 종목에 대해 첫 요청만 API를 호출하고, 나머지는 결과를 공유
        CompletableFuture<PriceResponse> newFuture = new CompletableFuture<>();
        CompletableFuture<PriceResponse> existing = ongoingRequests.putIfAbsent(stockCode, newFuture);

        // 이미 누군가 API 호출 중
        if (existing != null) {
            try {
                return existing.join();
            } catch (CompletionException e) {
                // 첫 번째 요청의 API 호출이 실패해서 completeExceptionally(e)로 채워진 경우, join()이 CompletionException을 던짐
                if (e.getCause() instanceof BusinessException) {
                    throw (BusinessException) e.getCause();
                }
                throw new BusinessException(ErrorCode.MARKET_API_FAILED);
            }
        }

        // 첫 번째 요청만 처리
        try {
            // 캐시 미스 -> 한투 API 호출 + PriceResponse 생성
            HantuPriceApiResponse.Output output = hantuMarketClient.fetchCurrentPrice(stockCode).output();
            PriceResponse response = PriceResponse.from(stockCode, output);

            // 캐시 저장
            priceCache.put(stockCode, response);
            priceCacheTimestamp.put(stockCode, System.currentTimeMillis());

            newFuture.complete(response);

            return response;

        } catch (Exception e) {
            newFuture.completeExceptionally(e); // 비즈니스 에러 추출
            throw new BusinessException(ErrorCode.MARKET_API_FAILED);
        }
        finally {
            ongoingRequests.remove(stockCode);
        }
    }

    public OrderbookResponse getOrderbook(String stockCode) {
        HantuOrderbookApiResponse.Output1 output = hantuMarketClient.fetchOrderbook(stockCode).output1();
        return OrderbookResponse.from(output);
    }

    @Override
    public BigDecimal getCurrentPrice(String stockCode) {
        PriceResponse response = getPrice(stockCode);
        return BigDecimal.valueOf(response.currentPrice());
    }

    @Override
    public String getCurrency(String stockCode) {
        return "KRW";
    }

    @Override
    public BigDecimal getUsdKrwRate() {
        return new BigDecimal("1508.00");
    }
}
