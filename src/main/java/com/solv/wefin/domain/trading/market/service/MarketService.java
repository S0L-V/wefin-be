package com.solv.wefin.domain.trading.market.service;

import com.solv.wefin.domain.trading.common.ExchangeRateProvider;
import com.solv.wefin.domain.trading.market.client.HantuMarketClient;
import com.solv.wefin.domain.trading.market.client.dto.HantuCandleApiResponse;
import com.solv.wefin.domain.trading.market.client.dto.HantuMinuteCandleApiResponse;
import com.solv.wefin.domain.trading.market.client.dto.HantuOrderbookApiResponse;
import com.solv.wefin.domain.trading.market.client.dto.HantuPriceApiResponse;
import com.solv.wefin.domain.trading.market.client.dto.HantuRankingApiResponse;
import com.solv.wefin.domain.trading.market.client.dto.HantuRecentTradeApiResponse;
import com.solv.wefin.domain.trading.market.client.dto.RankingType;
import com.solv.wefin.domain.trading.market.client.dto.StockRankingItem;
import com.solv.wefin.domain.trading.market.dto.CandleResponse;
import com.solv.wefin.domain.trading.market.dto.OrderbookResponse;
import com.solv.wefin.domain.trading.market.dto.PriceResponse;
import com.solv.wefin.domain.trading.common.MarketPriceProvider;
import com.solv.wefin.domain.trading.market.dto.RecentTradeResponse;
import com.solv.wefin.domain.trading.stock.service.StockService;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;

@Slf4j
@RequiredArgsConstructor
@Service
public class MarketService implements MarketPriceProvider, ExchangeRateProvider {

    private final HantuMarketClient hantuMarketClient;
    private final StockService stockService;

    private final ConcurrentHashMap<String, CompletableFuture<PriceResponse>> ongoingRequests = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, PriceResponse> priceCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> priceCacheTimestamp = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<StockRankingItem>> rankingCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> rankingCacheTimestamp = new ConcurrentHashMap<>();

    private static final long RANKING_CACHE_TTL_MS = 10000;
    private static final long STALE_RANKING_TTL_MS = 3000; // 외부 API 실패 시 stale 캐시 유지 시간
    private static final long CACHE_TTL_MS = 5000; // 5초
    private static final Set<String> VALID_PERIOD_CODES = Set.of("D", "W", "M", "Y");
    private static final Set<String> MINUTE_PERIOD_CODES = Set.of("1", "5", "15", "30", "60");

    public PriceResponse getPrice(String stockCode) {
        validateStockCode(stockCode);

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

    public List<StockRankingItem> getStockRanking(RankingType type) {

        String cacheKey = type.name();
        List<StockRankingItem> cached = getCachedRanking(cacheKey);
        if (cached != null) {
            return cached;
        }

        try {
            HantuRankingApiResponse response = switch (type) {
                case VOLUME -> hantuMarketClient.fetchVolumeRanking();
                case AMOUNT -> hantuMarketClient.fetchTradingAmountRanking();
                case RISING -> hantuMarketClient.fetchChangeRateRanking(true);
                case FALLING -> hantuMarketClient.fetchChangeRateRanking(false);
            };

            List<StockRankingItem> items = (response.output() != null)
                ? response.output().stream().map(StockRankingItem::from).toList()
                : List.of();

            // FALLING: 한투 API 가 급락 데이터를 정렬 없이 반환 — 서버에서 재정렬 후 순위 재부여
            // 동률 시 tie-break: 거래량 내림차순(큰 거래 우선) → 종목코드 오름차순(deterministic)
            if (!items.isEmpty() && type == RankingType.FALLING) {
                Comparator<StockRankingItem> fallingOrder = Comparator
                    .comparing(StockRankingItem::changeRate)
                    .thenComparing(StockRankingItem::volume, Comparator.reverseOrder())
                    .thenComparing(StockRankingItem::stockCode);

                var sorted = new ArrayList<>(items);
                sorted.sort(fallingOrder);
                items = java.util.stream.IntStream.range(0, sorted.size())
                    .mapToObj(i -> new StockRankingItem(
                        i + 1, sorted.get(i).stockCode(), sorted.get(i).stockName(),
                        sorted.get(i).currentPrice(), sorted.get(i).changeRate(),
                        sorted.get(i).changeAmount(), sorted.get(i).changeSign(),
                        sorted.get(i).volume(), sorted.get(i).tradingAmount()))
                    .toList();
            }

            rankingCache.put(cacheKey, items);
            rankingCacheTimestamp.put(cacheKey, System.currentTimeMillis());

            return items;
        } catch (Exception e) {
            // 실패 시 stale 캐시 있으면 반환 — 네거티브 캐시 효과로 rate limit 폭주 방지
            // TTL 을 짧게 조정해 장애 복구 시 빠르게 재시도
            List<StockRankingItem> stale = rankingCache.get(cacheKey);
            if (stale != null) {
                log.warn("랭킹 조회 실패 — stale 캐시 반환: type={}", type, e);
                rankingCacheTimestamp.put(cacheKey,
                    System.currentTimeMillis() - RANKING_CACHE_TTL_MS + STALE_RANKING_TTL_MS);
                return stale;
            }
            if (e instanceof BusinessException) throw (BusinessException) e;
            log.error("랭킹 조회 실패: type={}", type, e);
            throw new BusinessException(ErrorCode.MARKET_API_FAILED);
        }
    }

    private List<StockRankingItem> getCachedRanking(String type) {
        Long cachedTime = rankingCacheTimestamp.get(type);
        if (cachedTime != null && System.currentTimeMillis() - cachedTime < RANKING_CACHE_TTL_MS) {
            return rankingCache.get(type);
        }
        // TTL 만료 시 캐시는 유지 (stale fallback 용).
        // 성공 호출이 덮어쓰거나, 실패 시 catch 블록에서 stale 로 반환된다.
        // 랭킹 타입이 4개뿐이라 메모리 누적 부담 없음.
        return null;
    }

    public OrderbookResponse getOrderbook(String stockCode) {
        HantuOrderbookApiResponse.Output1 output = hantuMarketClient.fetchOrderbook(stockCode).output1();
        return OrderbookResponse.from(output);
    }

    @Override
    public BigDecimal getCurrentPrice(String stockCode) {
        PriceResponse response = getPrice(stockCode);
        return response.currentPrice();
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
        validateStockCode(stockCode);

        if (start.isAfter(end)) {
            throw new BusinessException(ErrorCode.MARKET_INVALID_DATE);
        }

        periodCode = periodCode.toUpperCase();

        // 분봉 조회
        if (MINUTE_PERIOD_CODES.contains(periodCode)) {
            return getMinuteCandles(stockCode, Integer.parseInt(periodCode));
        }

        // 일봉/주봉/월봉 조회
        if (!VALID_PERIOD_CODES.contains(periodCode)) {
            throw new BusinessException(ErrorCode.MARKET_INVALID_PERIOD_CODE);
        }

        HantuCandleApiResponse response = hantuMarketClient.fetchPeriodPrice(stockCode, start, end, periodCode);
        log.info("캔들 API 응답: {}", response);

        if (response == null) {
            return List.of();
        }

        if (response.output1() != null) {
            validateRtCode(response.output1().rt_cd());
        }

        if (response.output2() == null) {
            return List.of();
        }

        return response.output2().stream()
                    .map(CandleResponse::from)
                    .toList();
    }

    private List<CandleResponse> getMinuteCandles(String stockCode, int periodMinutes) {
        // 현재 KST 시간 기준으로 분봉 조회 (역순으로 반환됨)
        String inputHour = LocalDateTime.now(ZoneId.of("Asia/Seoul")).format(DateTimeFormatter.ofPattern("HHmmss"));
        HantuMinuteCandleApiResponse response = hantuMarketClient.fetchMinutePrice(stockCode, inputHour);
        log.info("분봉 API 응답: {}", response);

        if (response == null) {
            return List.of();
        }

        if (response.output1() != null) {
            validateRtCode(response.output1().rt_cd());
        }

        if (response.output2() == null) {
            return List.of();
        }

        // 한투 API가 내림차순(최신→과거)으로 반환하므로 오름차순 정렬
        List<CandleResponse> oneMinuteCandles = response.output2().stream()
                .map(CandleResponse::fromMinute)
                .sorted(Comparator.comparing(CandleResponse::date))
                .toList();

        // 1분봉이면 그대로 반환
        if (periodMinutes == 1) {
            return oneMinuteCandles;
        }

        // N분봉 집계
        return aggregateCandles(oneMinuteCandles, periodMinutes);
    }

    // date의 분을 period 단위로 내림한 값을 키로 그룹핑
    // 예: 09:33 → 09:30 (5분봉), 09:47 → 09:45 (15분봉)
    private List<CandleResponse> aggregateCandles(List<CandleResponse> candles, int periodMinutes) {
        LinkedHashMap<LocalDateTime, List<CandleResponse>> groups = new LinkedHashMap<>();

        for (CandleResponse candle : candles) {
            int minute = candle.date().getMinute();
            int floored = (minute / periodMinutes) * periodMinutes;
            LocalDateTime groupKey = candle.date().withMinute(floored).withSecond(0);

            groups.computeIfAbsent(groupKey, k -> new ArrayList<>()).add(candle);
        }

        // 각 그룹을 하나의 캔들로 합침
        return groups.entrySet().stream()
                .map(entry -> {
                    LocalDateTime time = entry.getKey();
                    List<CandleResponse> group = entry.getValue();

                    BigDecimal open = group.get(0).openPrice();
                    BigDecimal close = group.get(group.size()-1).closePrice();

                    BigDecimal high = group.get(0).highPrice();
                    BigDecimal low = group.get(0).lowPrice();
                    long volume = 0L;

                    for (CandleResponse candle : group) {
                        if (candle.highPrice().compareTo(high) > 0) high = candle.highPrice();
                        if (candle.lowPrice().compareTo(low) < 0) low = candle.lowPrice();
                        volume += candle.volume();
                    }

                    return new CandleResponse(time, open, high, low, close, volume);
                })
                .toList();
    }

    public List<RecentTradeResponse> getRecentTrades(String stockCode) {
        validateStockCode(stockCode);

        HantuRecentTradeApiResponse response = hantuMarketClient.fetchRecentTrades(stockCode);

        if (response == null) {
            return List.of();
        }

        if (response.output1() != null) {
            validateRtCode(response.output1().rt_cd());
        }

        if (response.output() == null) {
            return List.of();
        }

        return response.output().stream()
                .map(RecentTradeResponse::from)
                .toList();
    }

    private void validateStockCode(String stockCode) {
        if (!stockService.existsByCode(stockCode)) {
            throw new BusinessException(ErrorCode.MARKET_STOCK_NOT_FOUND);
        }
    }

    public void updatePriceCache(String stockCode, PriceResponse response) {
        priceCache.put(stockCode, response);
        priceCacheTimestamp.put(stockCode, System.currentTimeMillis());
    }

    private void validateRtCode(String rtCd) {
        if (rtCd != null && !"0".equals(rtCd)) {
            throw new BusinessException(ErrorCode.MARKET_API_FAILED);
        }
    }
}
