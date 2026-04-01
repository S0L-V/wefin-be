package com.solv.wefin.domain.trading.market.service;

import com.solv.wefin.domain.trading.common.ExchangeRateProvider;
import com.solv.wefin.domain.trading.market.client.HantuMarketClient;
import com.solv.wefin.domain.trading.market.client.dto.HantuOrderbookApiResponse;
import com.solv.wefin.domain.trading.market.client.dto.HantuPriceApiResponse;
import com.solv.wefin.domain.trading.market.dto.CandleResponse;
import com.solv.wefin.domain.trading.market.dto.OrderbookResponse;
import com.solv.wefin.domain.trading.market.dto.PriceResponse;
import com.solv.wefin.domain.trading.common.MarketPriceProvider;
import com.solv.wefin.domain.trading.stock.service.StockService;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.*;

@RequiredArgsConstructor
@Service
public class MarketService implements MarketPriceProvider, ExchangeRateProvider {

    private final HantuMarketClient hantuMarketClient;
    private final StockService stockService;

    private final ConcurrentHashMap<String, CompletableFuture<PriceResponse>> ongoingRequests = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, PriceResponse> priceCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> priceCacheTimestamp = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 5000; // 5초

    public PriceResponse getPrice(String stockCode) {
        if (!stockService.existsByCode(stockCode)) {
            throw new BusinessException(ErrorCode.MARKET_STOCK_NOT_FOUND);
        }

        // 캐시 확인
        PriceResponse cached = getCachedPrice(stockCode);
        if (cached != null) {
            return cached;
        }

        // thundering herd 방지: 같은 종목에 대해 첫 요청만 API를 호출하고, 나머지는 결과를 공유
        CompletableFuture<PriceResponse> newFuture = new CompletableFuture<>();
        CompletableFuture<PriceResponse> existing = ongoingRequests.putIfAbsent(stockCode, newFuture);

        // 이미 누군가 API 호출 중
        if (existing != null) {
            try {
                return existing.get(10, TimeUnit.SECONDS);
            } catch (ExecutionException e) {
                if (e.getCause() instanceof BusinessException) {
                    throw (BusinessException) e.getCause();
                }
                throw new BusinessException(ErrorCode.MARKET_API_FAILED);
            } catch (TimeoutException | InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BusinessException(ErrorCode.MARKET_API_FAILED);
            }
        }

        // 첫 번째 요청만 처리
        try {
            cached = getCachedPrice(stockCode);
            if (cached != null) {
                newFuture.complete(cached);
                return cached;
            }

            // 캐시 미스 -> 한투 API 호출 + PriceResponse 생성
            HantuPriceApiResponse.Output output = hantuMarketClient.fetchCurrentPrice(stockCode).output();
            PriceResponse response = PriceResponse.from(stockCode, output);

            priceCache.put(stockCode, response);
            priceCacheTimestamp.put(stockCode, System.currentTimeMillis());

            newFuture.complete(response);

            return response;

        } catch (Exception e) {
            newFuture.completeExceptionally(e);
            if (e instanceof BusinessException) {
                throw (BusinessException) e;
            }

            throw new BusinessException(ErrorCode.MARKET_API_FAILED);
        }
        finally {
            ongoingRequests.remove(stockCode);
        }
    }

    /**
     * 캐시에서 유효한 시세를 조회한다. TTL 만료 시 null 반환.
     */
    private PriceResponse getCachedPrice(String stockCode) {
        Long cachedTime = priceCacheTimestamp.get(stockCode);

        if (cachedTime != null && System.currentTimeMillis() - cachedTime < CACHE_TTL_MS) {
            return priceCache.get(stockCode);
        }

        if (cachedTime != null) {
            priceCache.remove(stockCode);
            priceCacheTimestamp.remove(stockCode);
        }

        return null;
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

    public List<CandleResponse> getCandles(String stockCode, LocalDate start, LocalDate end, String periodCode) {
        return hantuMarketClient.fetchPeriodPrice(stockCode, start, end, periodCode).output2().stream()
                .map(CandleResponse::from)
                .toList();
    }
}
