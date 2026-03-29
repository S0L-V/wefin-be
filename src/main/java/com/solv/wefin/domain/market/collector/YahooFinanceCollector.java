package com.solv.wefin.domain.market.collector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.solv.wefin.domain.market.dto.CollectedMarketData;
import com.solv.wefin.domain.market.entity.MarketSnapshot.ChangeDirection;
import com.solv.wefin.domain.market.entity.MarketSnapshot.MetricType;
import com.solv.wefin.domain.market.entity.MarketSnapshot.Unit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Yahoo Finance API에서 NASDAQ Composite 지수와 USD/KRW 환율을 수집한다.
 */
@Slf4j
@Component
public class YahooFinanceCollector implements MarketDataCollector {

    private static final String SOURCE_NAME = "YahooFinance";
    private static final String BASE_URL = "https://query1.finance.yahoo.com/v8/finance/chart";
    private static final String KOSPI_SYMBOL = "^KS11";
    private static final String NASDAQ_SYMBOL = "^IXIC";
    private static final String USD_KRW_SYMBOL = "KRW=X";
    private static final String USER_AGENT = "Mozilla/5.0 (compatible; WefinBot/1.0)";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public YahooFinanceCollector(@Qualifier("marketRestTemplate") RestTemplate restTemplate,
                                 ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<CollectedMarketData> collect() {
        List<CollectedMarketData> results = new ArrayList<>();

        collectKospi(results);
        collectNasdaq(results);
        collectUsdKrw(results);

        return results;
    }

    @Override
    public String getSourceName() {
        return SOURCE_NAME;
    }

    private void collectKospi(List<CollectedMarketData> results) {
        try {
            JsonNode meta = fetchChartMeta(KOSPI_SYMBOL);
            if (meta == null) return;

            BigDecimal price = BigDecimal.valueOf(meta.get("regularMarketPrice").asDouble());
            BigDecimal previousClose = BigDecimal.valueOf(meta.get("chartPreviousClose").asDouble());
            BigDecimal changeValue = price.subtract(previousClose);
            BigDecimal changeRate = calculateChangeRate(changeValue, previousClose);

            results.add(CollectedMarketData.builder()
                    .metricType(MetricType.KOSPI)
                    .label("코스피")
                    .value(price)
                    .changeRate(changeRate)
                    .changeValue(changeValue)
                    .unit(Unit.POINT)
                    .changeDirection(resolveDirection(changeValue))
                    .build());

            log.debug("KOSPI 수집 완료: {}", price);
        } catch (Exception e) {
            log.error("KOSPI 수집 실패: {}", e.getMessage(), e);
        }
    }

    private void collectNasdaq(List<CollectedMarketData> results) {
        try {
            JsonNode meta = fetchChartMeta(NASDAQ_SYMBOL);
            if (meta == null) return;

            BigDecimal price = BigDecimal.valueOf(meta.get("regularMarketPrice").asDouble());
            BigDecimal previousClose = BigDecimal.valueOf(meta.get("chartPreviousClose").asDouble());
            BigDecimal changeValue = price.subtract(previousClose);
            BigDecimal changeRate = calculateChangeRate(changeValue, previousClose);

            results.add(CollectedMarketData.builder()
                    .metricType(MetricType.NASDAQ)
                    .label("나스닥")
                    .value(price)
                    .changeRate(changeRate)
                    .changeValue(changeValue)
                    .unit(Unit.POINT)
                    .changeDirection(resolveDirection(changeValue))
                    .build());

            log.debug("NASDAQ 수집 완료: {}", price);
        } catch (Exception e) {
            log.error("NASDAQ 수집 실패: {}", e.getMessage(), e);
        }
    }

    private void collectUsdKrw(List<CollectedMarketData> results) {
        try {
            JsonNode meta = fetchChartMeta(USD_KRW_SYMBOL);
            if (meta == null) return;

            BigDecimal price = BigDecimal.valueOf(meta.get("regularMarketPrice").asDouble());
            BigDecimal previousClose = BigDecimal.valueOf(meta.get("chartPreviousClose").asDouble());
            BigDecimal changeValue = price.subtract(previousClose);
            BigDecimal changeRate = calculateChangeRate(changeValue, previousClose);

            results.add(CollectedMarketData.builder()
                    .metricType(MetricType.USD_KRW)
                    .label("원/달러 환율")
                    .value(price)
                    .changeRate(changeRate)
                    .changeValue(changeValue)
                    .unit(Unit.KRW)
                    .changeDirection(resolveDirection(changeValue))
                    .build());

            log.debug("USD/KRW 수집 완료: {}", price);
        } catch (Exception e) {
            log.error("USD/KRW 수집 실패: {}", e.getMessage(), e);
        }
    }

    /**
     * Yahoo Finance chart API를 호출하여 meta 정보를 반환한다.
     *
     * @param symbol Yahoo Finance 심볼 (ex. ^IXIC, KRW=X)
     * @return meta JSON 노드, 실패 시 null
     */
    private JsonNode fetchChartMeta(String symbol) {
        String url = BASE_URL + "/" + symbol + "?interval=1d&range=2d";

        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", USER_AGENT);

        ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers), String.class);

        String body = response.getBody();
        if (body == null || body.isBlank()) {
            log.warn("Yahoo Finance {} 응답 body가 비어 있습니다", symbol);
            return null;
        }

        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode results = root.path("chart").path("result");

            if (results.isMissingNode() || results.isEmpty()) {
                log.warn("Yahoo Finance {} 응답에 데이터가 없습니다", symbol);
                return null;
            }

            return results.get(0).get("meta");
        } catch (Exception e) {
            log.error("Yahoo Finance {} 응답 파싱 실패: {}", symbol, e.getMessage(), e);
            return null;
        }
    }

    private BigDecimal calculateChangeRate(BigDecimal changeValue, BigDecimal previousClose) {
        if (previousClose.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return changeValue.divide(previousClose, 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private ChangeDirection resolveDirection(BigDecimal changeValue) {
        int cmp = changeValue.compareTo(BigDecimal.ZERO);
        if (cmp > 0) return ChangeDirection.UP;
        if (cmp < 0) return ChangeDirection.DOWN;
        return ChangeDirection.FLAT;
    }
}
