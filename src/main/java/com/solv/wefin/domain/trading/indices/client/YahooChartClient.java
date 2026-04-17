package com.solv.wefin.domain.trading.indices.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.solv.wefin.domain.trading.indices.dto.ChangeDirection;
import com.solv.wefin.domain.trading.indices.dto.IndexCode;
import com.solv.wefin.domain.trading.indices.dto.IndexQuote;
import com.solv.wefin.domain.trading.indices.dto.MarketStatus;
import com.solv.wefin.domain.trading.indices.dto.SparklineInterval;
import com.solv.wefin.domain.trading.indices.dto.SparklinePoint;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Yahoo Finance chart API 클라이언트.
 *
 * <p>{@code interval=1m&range=1d} 로 호출하여 meta(현재값/전일종가/장상태) + close 분봉 배열을 함께 받아
 * {@link IndexQuote} 로 조립한다.</p>
 */
@Slf4j
@Component
public class YahooChartClient {

    private static final String BASE_URL = "https://query1.finance.yahoo.com/v8/finance/chart";
    private static final String USER_AGENT = "Mozilla/5.0 (compatible; WefinBot/1.0)";
    private static final int RATE_SCALE = 4;
    private static final BigDecimal PERCENT_MULTIPLIER = new BigDecimal("100");
    private static final long SESSION_GAP_MULTIPLIER = 3L;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public YahooChartClient(@Qualifier("marketRestTemplate") RestTemplate restTemplate,
                            ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 단일 지수의 현재값 + 스파크라인 배열을 조회한다.
     *
     * @param code             지수 코드
     * @param interval         분봉 간격 (1m / 5m / 15m)
     * @param sparklinePoints  스파크라인 포인트 개수 (최근 N개)
     * @return IndexQuote
     */
    public IndexQuote fetchQuote(IndexCode code, SparklineInterval interval, int sparklinePoints) {
        URI uri = buildUri(code, interval);

        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", USER_AGENT);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    uri, HttpMethod.GET, new HttpEntity<>(headers), String.class);

            String body = response.getBody();
            if (body == null || body.isBlank()) {
                throw new BusinessException(ErrorCode.MARKET_API_FAILED);
            }

            JsonNode root = objectMapper.readTree(body);
            JsonNode result = root.path("chart").path("result").path(0);
            if (result.isMissingNode()) {
                log.warn("Yahoo {} 응답에 result 없음", code);
                throw new BusinessException(ErrorCode.MARKET_API_FAILED);
            }

            JsonNode meta = result.path("meta");
            if (!meta.has("regularMarketPrice")) {
                log.warn("Yahoo {} meta 필수 필드 누락: {}", code, meta);
                throw new BusinessException(ErrorCode.MARKET_API_FAILED);
            }

            BigDecimal currentValue = BigDecimal.valueOf(meta.get("regularMarketPrice").asDouble());
            BigDecimal previousClose = resolvePreviousClose(meta);
            BigDecimal changeValue = currentValue.subtract(previousClose);
            BigDecimal changeRate = calculateChangeRate(changeValue, previousClose);
            ChangeDirection direction = resolveDirection(changeValue);
            MarketStatus marketStatus = resolveMarketStatus(meta.path("marketState").asText(""));

            List<SparklinePoint> sparkline = extractSparkline(result, interval, sparklinePoints);

            return new IndexQuote(
                    code,
                    code.getLabel(),
                    currentValue,
                    changeValue,
                    changeRate,
                    direction,
                    code.isDelayed(),
                    marketStatus,
                    sparkline
            );

        } catch (BusinessException e) {
            throw e;
        } catch (RestClientException | JsonProcessingException e) {
            log.error("Yahoo {} 조회 실패", code, e);
            throw new BusinessException(ErrorCode.MARKET_API_FAILED);
        }
    }

    /**
     * Yahoo chart API URI 를 조립한다. 심볼(^GSPC, ^IXIC 등) 의 특수문자가 안전하게 인코딩되도록
     * {@link UriComponentsBuilder} 로 구성한 뒤 {@link URI} 로 반환한다.
     * <p>{@code String} 이 아닌 {@code URI} 로 넘겨야 RestTemplate 의 자동 인코딩으로 인한 이중 인코딩을 피할 수 있다.</p>
     */
    private URI buildUri(IndexCode code, SparklineInterval interval) {
        return UriComponentsBuilder.fromUriString(BASE_URL)
                .pathSegment(code.getYahooSymbol())
                .queryParam("interval", interval.getLabel())
                .queryParam("range", resolveRange(interval))
                .build()
                .toUri();
    }

    /**
     * result.timestamp[] + indicators.quote[0].close[] 를 엮어 최근 N개 SparklinePoint 리스트를 만든다.
     * <p>파이프라인:
     * <ol>
     *   <li>close 가 null 인 포인트 제외 (장 시작 전/마감 후 빈 슬롯)</li>
     *   <li>세션 경계 감지 — interval 3배 넘는 시간 간격이 있으면 split, 가장 최신 세션만 유지</li>
     *   <li>최신 sparklinePoints 개수만큼 꼬리 잘라 반환</li>
     * </ol>
     * </p>
     */
    private List<SparklinePoint> extractSparkline(JsonNode result, SparklineInterval interval, int sparklinePoints) {
        JsonNode timestamps = result.path("timestamp");
        JsonNode quoteArr = result.path("indicators").path("quote");
        if (!timestamps.isArray() || !quoteArr.isArray() || quoteArr.isEmpty()) {
            return List.of();
        }
        JsonNode closes = quoteArr.path(0).path("close");
        if (!closes.isArray()) {
            return List.of();
        }

        int size = Math.min(timestamps.size(), closes.size());
        List<SparklinePoint> points = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            JsonNode closeNode = closes.get(i);
            if (closeNode == null || closeNode.isNull()) continue;

            long epochSec = timestamps.get(i).asLong();
            BigDecimal value = BigDecimal.valueOf(closeNode.asDouble());
            points.add(new SparklinePoint(Instant.ofEpochSecond(epochSec), value));
        }

        List<SparklinePoint> latestSession = filterLatestSession(points, interval);

        if (latestSession.size() <= sparklinePoints) {
            return List.copyOf(latestSession);
        }
        // subList 는 원본 view 라 외부 변경에 노출됨 → copyOf 로 불변 리스트 반환
        return List.copyOf(latestSession.subList(latestSession.size() - sparklinePoints, latestSession.size()));
    }

    /**
     * 타임스탬프 간격이 interval 의 3배를 초과하는 지점(세션 경계)을 찾아,
     * 가장 최근 세션의 포인트만 반환한다.
     * <p>예: 5분봉에서 15분 이상 gap 이면 점심시간 or 장마감/다음날로 간주하고 그 뒤부터만 유지.</p>
     */
    private List<SparklinePoint> filterLatestSession(List<SparklinePoint> points, SparklineInterval interval) {
        if (points.size() < 2) return points;

        long gapThresholdSec = interval.getIntervalSeconds() * SESSION_GAP_MULTIPLIER;
        int sessionStart = 0;
        for (int i = 1; i < points.size(); i++) {
            long gap = points.get(i).t().getEpochSecond() - points.get(i - 1).t().getEpochSecond();
            if (gap > gapThresholdSec) {
                sessionStart = i;
            }
        }
        // 중간 가공 단계 — caller(extractSparkline) 가 최종 copyOf 로 감싼다
        return points.subList(sessionStart, points.size());
    }

    /**
     * 진짜 전일 종가를 결정한다.
     * <p>Yahoo meta 에는 2개의 유사 필드가 있는데 의미가 다름:
     * <ul>
     *   <li>{@code previousClose} / {@code regularMarketPreviousClose}: 실제 직전 영업일 종가</li>
     *   <li>{@code chartPreviousClose}: 요청한 {@code range} 시작 이전의 종가 — {@code range=2d} 면 2일 전 종가</li>
     * </ul>
     * {@code range=2d} 로 호출하므로 {@code chartPreviousClose} 를 쓰면 이틀 전 값이 되어 변동률 왜곡.
     * 실제 전일 종가인 {@code previousClose} 를 우선 사용하고, 누락 시에만 fallback.
     * </p>
     */
    private BigDecimal resolvePreviousClose(JsonNode meta) {
        if (meta.has("previousClose") && !meta.get("previousClose").isNull()) {
            return BigDecimal.valueOf(meta.get("previousClose").asDouble());
        }
        if (meta.has("regularMarketPreviousClose") && !meta.get("regularMarketPreviousClose").isNull()) {
            return BigDecimal.valueOf(meta.get("regularMarketPreviousClose").asDouble());
        }
        if (meta.has("chartPreviousClose") && !meta.get("chartPreviousClose").isNull()) {
            log.warn("Yahoo meta 에 previousClose 없음 — chartPreviousClose fallback (range 이전 종가라 부정확할 수 있음)");
            return BigDecimal.valueOf(meta.get("chartPreviousClose").asDouble());
        }
        throw new BusinessException(ErrorCode.MARKET_API_FAILED);
    }

    private BigDecimal calculateChangeRate(BigDecimal changeValue, BigDecimal previousClose) {
        if (previousClose.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return changeValue.divide(previousClose, RATE_SCALE, RoundingMode.HALF_UP)
                .multiply(PERCENT_MULTIPLIER)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private ChangeDirection resolveDirection(BigDecimal changeValue) {
        int cmp = changeValue.compareTo(BigDecimal.ZERO);
        if (cmp > 0) return ChangeDirection.UP;
        if (cmp < 0) return ChangeDirection.DOWN;
        return ChangeDirection.FLAT;
    }

    /**
     * Yahoo meta.marketState 매핑.
     * REGULAR → OPEN, PRE → PRE_OPEN, 나머지(POST/POSTPOST/CLOSED/빈값) → CLOSED
     */
    private MarketStatus resolveMarketStatus(String raw) {
        if (raw == null || raw.isBlank()) return MarketStatus.CLOSED;
        return switch (raw.toUpperCase()) {
            case "REGULAR" -> MarketStatus.OPEN;
            case "PRE" -> MarketStatus.PRE_OPEN;
            default -> MarketStatus.CLOSED;
        };
    }

    /**
     * interval 에 맞는 range 를 결정한다.
     * Yahoo 제약상 1m 은 7일까지, 5m/15m 은 60일까지 허용.
     * 정규장 하루치(1d) 면 1m 에서 충분, 5m/15m 은 2d 로 넉넉히 받아 세션 필터로 오늘치만 추림.
     */
    private String resolveRange(SparklineInterval interval) {
        return switch (interval) {
            case ONE_MIN -> "1d";
            case FIVE_MIN, FIFTEEN_MIN -> "2d";
        };
    }
}
