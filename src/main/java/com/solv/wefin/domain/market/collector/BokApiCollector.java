package com.solv.wefin.domain.market.collector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.solv.wefin.domain.market.dto.CollectedMarketData;
import com.solv.wefin.domain.market.entity.MarketSnapshot.ChangeDirection;
import com.solv.wefin.domain.market.entity.MarketSnapshot.MetricType;
import com.solv.wefin.domain.market.entity.MarketSnapshot.Unit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

/**
 * 한국은행 ECOS API에서 기준금리를 수집한다.
 */
@Slf4j
@Component
public class BokApiCollector implements MarketDataCollector {

    private static final String SOURCE_NAME = "BOK";
    private static final String STAT_CODE = "722Y001"; // ECOS 기준금리 통계표 코드
    private static final String ITEM_CODE = "0101000"; // 기준금리 항목 코드
    private static final String PERIOD_TYPE = "M"; // 월 단위 조회
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyyMM"); // ECOS 월 파라미터 포맷

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String baseUrl;

    public BokApiCollector(@Qualifier("marketRestTemplate") RestTemplate restTemplate,
                           ObjectMapper objectMapper,
                           @Value("${market.bok.api-key:}") String apiKey,
                           @Value("${market.bok.base-url:https://ecos.bok.or.kr/api}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
    }


    /**
     * 한국은행 ECOS API를 호출해 기준금리를 수집한다.
     * 최근 2개월 데이터를 조회한 뒤 최신 값과 직전 값을 비교해 변동 방향 및 변동 값을 계산한다.
     *
     * @return 수집 결과가 있으면 1건의 리스트, 없으면 빈 리스트
     */
    @Override
    public List<CollectedMarketData> collect() {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("BOK API 키가 설정되지 않아 기준금리 수집을 스킵합니다");
            return Collections.emptyList();
        }

        try {
            String endMonth = LocalDate.now().format(MONTH_FORMAT);
            String startMonth = LocalDate.now().minusMonths(2).format(MONTH_FORMAT);

            String url = String.format("%s/StatisticSearch/%s/json/kr/1/2/%s/%s/%s/%s/%s",
                    baseUrl, apiKey, STAT_CODE, PERIOD_TYPE, startMonth, endMonth, ITEM_CODE);

            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            String body = response.getBody();
            if (body == null || body.isBlank()) {
                log.warn("한국은행 API 응답 body가 비어 있습니다");
                return Collections.emptyList();
            }
            JsonNode root = objectMapper.readTree(body);

            JsonNode statSearch = root.get("StatisticSearch");
            if (statSearch == null) {
                log.warn("한국은행 API 응답에 StatisticSearch가 없습니다: {}", response.getBody());
                return Collections.emptyList();
            }

            JsonNode rows = statSearch.get("row");
            if (rows == null || rows.isEmpty()) {
                log.warn("한국은행 API 응답에 데이터가 없습니다");
                return Collections.emptyList();
            }

            JsonNode latest = rows.get(rows.size() - 1);
            if (!latest.has("DATA_VALUE")) {
                log.warn("한국은행 API 응답에 DATA_VALUE가 없습니다");
                return Collections.emptyList();
            }
            BigDecimal currentRate = new BigDecimal(latest.get("DATA_VALUE").asText());

            ChangeDirection direction = ChangeDirection.FLAT;
            BigDecimal changeValue = BigDecimal.ZERO;
            if (rows.size() >= 2) {
                JsonNode previous = rows.get(rows.size() - 2);
                if (previous.has("DATA_VALUE")) {
                    BigDecimal previousRate = new BigDecimal(previous.get("DATA_VALUE").asText());
                    changeValue = currentRate.subtract(previousRate);
                    direction = resolveDirection(changeValue);
                }
            }

            CollectedMarketData data = CollectedMarketData.builder()
                    .metricType(MetricType.BASE_RATE)
                    .label("한국 기준금리")
                    .value(currentRate)
                    .changeRate(BigDecimal.ZERO)
                    .changeValue(changeValue)
                    .unit(Unit.PERCENT)
                    .changeDirection(direction)
                    .build();

            log.debug("기준금리 수집 완료: {}%", currentRate);
            return List.of(data);
        } catch (Exception e) {
            log.error("기준금리 수집 실패: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    @Override
    public String getSourceName() {
        return SOURCE_NAME;
    }

}
