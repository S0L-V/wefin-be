package com.solv.wefin.domain.trading.indices.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.solv.wefin.domain.trading.indices.dto.ChangeDirection;
import com.solv.wefin.domain.trading.indices.dto.IndexCode;
import com.solv.wefin.domain.trading.indices.dto.IndexQuote;
import com.solv.wefin.domain.trading.indices.dto.MarketStatus;
import com.solv.wefin.domain.trading.indices.dto.SparklineInterval;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class YahooChartClientTest {

    private RestTemplate restTemplate;
    private MockRestServiceServer mockServer;
    private YahooChartClient client;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        mockServer = MockRestServiceServer.createServer(restTemplate);
        client = new YahooChartClient(restTemplate, new ObjectMapper());
    }

    @Test
    void 정상_응답_파싱() {
        String body = """
            {
              "chart": {
                "result": [{
                  "meta": {
                    "regularMarketPrice": 6226.05,
                    "previousClose": 6091.39,
                    "chartPreviousClose": 5967.75,
                    "marketState": "REGULAR"
                  },
                  "timestamp": [1713254400, 1713254460, 1713254520],
                  "indicators": {
                    "quote": [{
                      "close": [6150.0, 6170.5, 6226.05]
                    }]
                  }
                }]
              }
            }
            """;
        mockServer.expect(requestTo(
                "https://query1.finance.yahoo.com/v8/finance/chart/%5EKS11?interval=1m&range=1d"))
            .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        IndexQuote quote = client.fetchQuote(IndexCode.KOSPI, SparklineInterval.ONE_MIN, 30);

        assertThat(quote.code()).isEqualTo(IndexCode.KOSPI);
        assertThat(quote.currentValue()).isEqualByComparingTo("6226.05");
        assertThat(quote.changeValue()).isEqualByComparingTo("134.66");
        assertThat(quote.changeRate()).isEqualByComparingTo("2.21");
        assertThat(quote.changeDirection()).isEqualTo(ChangeDirection.UP);
        assertThat(quote.marketStatus()).isEqualTo(MarketStatus.OPEN);
        assertThat(quote.sparkline()).hasSize(3);

        mockServer.verify();
    }

    @Test
    void previousClose_우선_사용_chartPreviousClose_무시() {
        String body = """
            {"chart":{"result":[{
              "meta":{"regularMarketPrice":6226.05,"previousClose":6091.39,"chartPreviousClose":5967.75,"marketState":"CLOSED"},
              "timestamp":[1713254400],"indicators":{"quote":[{"close":[6226.05]}]}
            }]}}
            """;
        mockServer.expect(requestTo(
                "https://query1.finance.yahoo.com/v8/finance/chart/%5EKS11?interval=1m&range=1d"))
            .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        IndexQuote quote = client.fetchQuote(IndexCode.KOSPI, SparklineInterval.ONE_MIN, 30);

        // chartPreviousClose(5967.75) 썼으면 +4.33%, previousClose(6091.39) 썼으면 +2.21%
        assertThat(quote.changeRate()).isEqualByComparingTo("2.21");
    }

    @Test
    void previousClose_없을때_chartPreviousClose_fallback() {
        String body = """
            {"chart":{"result":[{
              "meta":{"regularMarketPrice":6226.05,"chartPreviousClose":5967.75,"marketState":"CLOSED"},
              "timestamp":[1713254400],"indicators":{"quote":[{"close":[6226.05]}]}
            }]}}
            """;
        mockServer.expect(requestTo(
                "https://query1.finance.yahoo.com/v8/finance/chart/%5EKS11?interval=1m&range=1d"))
            .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        IndexQuote quote = client.fetchQuote(IndexCode.KOSPI, SparklineInterval.ONE_MIN, 30);

        assertThat(quote.changeRate()).isEqualByComparingTo("4.33");
    }

    @Test
    void 세션_경계_감지_최근_세션만_유지() {
        long t1 = 1713168000L;
        long t2 = t1 + 60;
        long t3 = t2 + 60;
        long t4 = t3 + 60 * 60 * 18;
        long t5 = t4 + 60;
        long t6 = t5 + 60;
        String body = String.format("""
            {"chart":{"result":[{
              "meta":{"regularMarketPrice":6226.05,"previousClose":6091.39,"marketState":"REGULAR"},
              "timestamp":[%d,%d,%d,%d,%d,%d],
              "indicators":{"quote":[{"close":[6000.0,6010.0,6020.0,6200.0,6210.0,6226.05]}]}
            }]}}
            """, t1, t2, t3, t4, t5, t6);
        mockServer.expect(requestTo(
                "https://query1.finance.yahoo.com/v8/finance/chart/%5EKS11?interval=1m&range=1d"))
            .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        IndexQuote quote = client.fetchQuote(IndexCode.KOSPI, SparklineInterval.ONE_MIN, 30);

        assertThat(quote.sparkline()).hasSize(3);
        assertThat(quote.sparkline().get(0).t()).isEqualTo(Instant.ofEpochSecond(t4));
        assertThat(quote.sparkline().get(2).t()).isEqualTo(Instant.ofEpochSecond(t6));
    }

    @Test
    void null_close_포인트는_스킵() {
        String body = """
            {"chart":{"result":[{
              "meta":{"regularMarketPrice":6226.05,"previousClose":6091.39,"marketState":"CLOSED"},
              "timestamp":[1713254400,1713254460,1713254520],
              "indicators":{"quote":[{"close":[6150.0,null,6226.05]}]}
            }]}}
            """;
        mockServer.expect(requestTo(
                "https://query1.finance.yahoo.com/v8/finance/chart/%5EKS11?interval=1m&range=1d"))
            .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        IndexQuote quote = client.fetchQuote(IndexCode.KOSPI, SparklineInterval.ONE_MIN, 30);

        assertThat(quote.sparkline()).hasSize(2);
        assertThat(quote.sparkline()).extracting(p -> p.v())
            .containsExactly(new BigDecimal("6150.0"), new BigDecimal("6226.05"));
    }

    @Test
    void sparklinePoints_보다_많으면_최근_N개만_반환() {
        String body = """
            {"chart":{"result":[{
              "meta":{"regularMarketPrice":6226.05,"previousClose":6091.39,"marketState":"CLOSED"},
              "timestamp":[1713254400,1713254460,1713254520,1713254580,1713254640],
              "indicators":{"quote":[{"close":[100.0,200.0,300.0,400.0,500.0]}]}
            }]}}
            """;
        mockServer.expect(requestTo(
                "https://query1.finance.yahoo.com/v8/finance/chart/%5EKS11?interval=1m&range=1d"))
            .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        IndexQuote quote = client.fetchQuote(IndexCode.KOSPI, SparklineInterval.ONE_MIN, 3);

        assertThat(quote.sparkline()).hasSize(3);
        assertThat(quote.sparkline()).extracting(p -> p.v())
            .containsExactly(new BigDecimal("300.0"), new BigDecimal("400.0"), new BigDecimal("500.0"));
    }

    @Test
    void marketState_매핑() {
        assertMarketStateMapping("REGULAR", MarketStatus.OPEN);
        assertMarketStateMapping("PRE", MarketStatus.PRE_OPEN);
        assertMarketStateMapping("POST", MarketStatus.CLOSED);
        assertMarketStateMapping("POSTPOST", MarketStatus.CLOSED);
        assertMarketStateMapping("CLOSED", MarketStatus.CLOSED);
    }

    private void assertMarketStateMapping(String rawState, MarketStatus expected) {
        RestTemplate local = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(local);
        YahooChartClient localClient = new YahooChartClient(local, new ObjectMapper());

        String body = String.format("""
            {"chart":{"result":[{
              "meta":{"regularMarketPrice":6226.05,"previousClose":6091.39,"marketState":"%s"},
              "timestamp":[1713254400],"indicators":{"quote":[{"close":[6226.05]}]}
            }]}}
            """, rawState);
        server.expect(requestTo(
                "https://query1.finance.yahoo.com/v8/finance/chart/%5EKS11?interval=1m&range=1d"))
            .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        IndexQuote quote = localClient.fetchQuote(IndexCode.KOSPI, SparklineInterval.ONE_MIN, 30);
        assertThat(quote.marketStatus()).as("state=%s", rawState).isEqualTo(expected);
    }

    @Test
    void regularMarketPrice_없으면_MARKET_API_FAILED() {
        String body = """
            {"chart":{"result":[{
              "meta":{"previousClose":6091.39,"marketState":"CLOSED"},
              "timestamp":[1713254400],"indicators":{"quote":[{"close":[6226.05]}]}
            }]}}
            """;
        mockServer.expect(requestTo(
                "https://query1.finance.yahoo.com/v8/finance/chart/%5EKS11?interval=1m&range=1d"))
            .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() ->
            client.fetchQuote(IndexCode.KOSPI, SparklineInterval.ONE_MIN, 30))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(ErrorCode.MARKET_API_FAILED);
    }

    @Test
    void result_없으면_MARKET_API_FAILED() {
        String body = """
            {"chart":{"result":[]}}
            """;
        mockServer.expect(requestTo(
                "https://query1.finance.yahoo.com/v8/finance/chart/%5EKS11?interval=1m&range=1d"))
            .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() ->
            client.fetchQuote(IndexCode.KOSPI, SparklineInterval.ONE_MIN, 30))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    void HTTP_5xx_에러시_MARKET_API_FAILED() {
        mockServer.expect(requestTo(
                "https://query1.finance.yahoo.com/v8/finance/chart/%5EKS11?interval=1m&range=1d"))
            .andRespond(withServerError());

        assertThatThrownBy(() ->
            client.fetchQuote(IndexCode.KOSPI, SparklineInterval.ONE_MIN, 30))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(ErrorCode.MARKET_API_FAILED);
    }

    @Test
    void 해외지수_심볼_URL_인코딩_검증() {
        String body = """
            {"chart":{"result":[{
              "meta":{"regularMarketPrice":7022.95,"previousClose":6886.24,"marketState":"CLOSED"},
              "timestamp":[1713254400],"indicators":{"quote":[{"close":[7022.95]}]}
            }]}}
            """;
        mockServer.expect(requestTo(
                "https://query1.finance.yahoo.com/v8/finance/chart/%5EGSPC?interval=5m&range=2d"))
            .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        IndexQuote quote = client.fetchQuote(IndexCode.SP500, SparklineInterval.FIVE_MIN, 30);

        assertThat(quote.code()).isEqualTo(IndexCode.SP500);
        assertThat(quote.isDelayed()).isTrue();
    }
}
