package com.solv.wefin.domain.market.trend.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.solv.wefin.domain.game.openai.OpenAiProperties;
import com.solv.wefin.domain.market.entity.MarketSnapshot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * OpenAI로 오늘의 금융 동향(title + summary + 인사이트 카드 + 키워드)을 생성한다
 *
 * 입력: 4개 시장 지표 스냅샷 + 최근 24시간 주요 클러스터 제목/요약 + 태그 집계(STOCK/TOPIC).
 * 출력 JSON: {@code title, summary, insightCards[4], relatedKeywords[]}.
 */
@Slf4j
@Component
public class OpenAiMarketTrendClient {

    /** 프롬프트에 포함할 대표 클러스터 최대 개수 */
    public static final int MAX_CLUSTERS_IN_PROMPT = 15;
    /** 태그 집계 전달 상위 N */
    public static final int MAX_TAGS_IN_PROMPT = 10;
    /** 인사이트 카드 요구 개수 (고정) */
    public static final int REQUIRED_INSIGHT_CARDS = 4;

    private final RestClient restClient;
    private final OpenAiProperties properties;
    private final ObjectMapper objectMapper;

    public OpenAiMarketTrendClient(
            @Qualifier("openAiRestClient") RestClient restClient,
            OpenAiProperties properties,
            ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * 금융 동향을 생성한다
     *
     * @param snapshots        4개 시장 지표
     * @param clusterSummaries 프롬프트에 포함할 주요 클러스터 (인덱스 = 위치+1 → 호출자가 clusterId 매핑)
     * @param risingStocks     떠오르는 STOCK 태그 이름 목록 (집계 상위 N)
     * @param risingTopics     떠오르는 TOPIC 태그 이름 목록 (집계 상위 N)
     * @return 파싱된 콘텐츠 (relatedClusterIndices는 prompt index 기준)
     */
    public MarketTrendRawResult generateTrend(List<MarketSnapshot> snapshots,
                                              List<ClusterSummary> clusterSummaries,
                                              List<String> risingStocks,
                                              List<String> risingTopics) {
        String userMessage = buildUserMessage(snapshots, clusterSummaries, risingStocks, risingTopics);

        ChatRequest request = new ChatRequest(
                properties.getModel(),
                List.of(
                        new ChatRequest.Message("system", SYSTEM_PROMPT),
                        new ChatRequest.Message("user", userMessage)
                ),
                properties.getMaxTokens(),
                properties.getTemperature(),
                new ChatRequest.ResponseFormat("json_object")
        );

        ChatResponse response;
        try {
            response = restClient.post()
                    .uri("/v1/chat/completions")
                    .body(request)
                    .retrieve()
                    .body(ChatResponse.class);
        } catch (RestClientResponseException e) {
            throw new MarketTrendAiException(
                    "OpenAI Market Trend API HTTP 오류: " + e.getStatusCode(), e);
        } catch (Exception e) {
            throw new MarketTrendAiException(
                    "OpenAI Market Trend API 호출 실패: " + e.getMessage(), e);
        }

        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw new MarketTrendAiException("OpenAI Market Trend API 응답이 비어있습니다", null);
        }

        ChatResponse.Choice choice = response.choices().get(0);
        if (choice == null || choice.message() == null) {
            throw new MarketTrendAiException("OpenAI Market Trend API message 누락", null);
        }
        String content = choice.message().content();
        if (content == null || content.isBlank()) {
            throw new MarketTrendAiException("OpenAI Market Trend API content 비어있음", null);
        }
        return parse(content);
    }

    private String buildUserMessage(List<MarketSnapshot> snapshots,
                                    List<ClusterSummary> clusterSummaries,
                                    List<String> risingStocks,
                                    List<String> risingTopics) {
        StringBuilder sb = new StringBuilder();

        sb.append("### 시장 지표\n");
        for (MarketSnapshot s : snapshots) {
            sb.append("- ").append(s.getLabel()).append(": ").append(s.getValue());
            if (s.getChangeRate() != null) {
                sb.append(" (").append(s.getChangeDirection()).append(" ").append(s.getChangeRate()).append("%)");
            }
            sb.append("\n");
        }

        sb.append("\n### 최근 24시간 주요 뉴스 클러스터\n");
        int limit = Math.min(clusterSummaries.size(), MAX_CLUSTERS_IN_PROMPT);
        for (int i = 0; i < limit; i++) {
            ClusterSummary c = clusterSummaries.get(i);
            sb.append("[").append(i + 1).append("] ").append(c.title());
            if (c.summary() != null && !c.summary().isBlank()) {
                sb.append("\n    요약: ").append(c.summary());
            }
            sb.append("\n");
        }

        sb.append("\n### 떠오르는 종목 (언급 상위)\n");
        sb.append(truncate(risingStocks, MAX_TAGS_IN_PROMPT).stream().collect(Collectors.joining(", ")));

        sb.append("\n\n### 떠오르는 주제 (언급 상위)\n");
        sb.append(truncate(risingTopics, MAX_TAGS_IN_PROMPT).stream().collect(Collectors.joining(", ")));

        return sb.toString();
    }

    private static <T> List<T> truncate(List<T> list, int n) {
        if (list == null) return List.of();
        return list.size() <= n ? list : list.subList(0, n);
    }

    private MarketTrendRawResult parse(String json) {
        try {
            RawResponse raw = objectMapper.readValue(json, RawResponse.class);
            if (raw == null) {
                throw new MarketTrendAiException("Market Trend JSON이 비어있습니다", null);
            }

            List<InsightCardRaw> rawCards = raw.insightCards() != null ? raw.insightCards() : List.of();
            List<ParsedCard> parsedCards = new ArrayList<>();
            for (InsightCardRaw c : rawCards) {
                if (c == null) continue;
                parsedCards.add(new ParsedCard(
                        c.headline(),
                        c.body(),
                        c.relatedClusterIndices() != null ? c.relatedClusterIndices() : List.of()
                ));
            }

            return new MarketTrendRawResult(
                    raw.title(),
                    raw.summary(),
                    parsedCards,
                    raw.relatedKeywords() != null ? raw.relatedKeywords() : List.of()
            );
        } catch (JsonProcessingException e) {
            throw new MarketTrendAiException("Market Trend JSON 파싱 실패: " + e.getOriginalMessage(), e);
        }
    }

    // ── 시스템 프롬프트 ────────────────────────────────────────

    private static final String SYSTEM_PROMPT = """
            당신은 한국 금융시장 전문 애널리스트입니다.
            주어진 시장 지표와 최근 24시간 주요 뉴스 클러스터를 종합하여 오늘의 금융 동향을 작성합니다.

            작성 규칙:
            1. title: 오늘 시장 핵심 한 줄 요약 (50자 이내, 한글)
            2. summary: 3~4문단 시장 전반 분석 (500~800자)
               - 시장 지표의 변동과 뉴스 흐름을 연결해 설명
               - 투자자가 오늘 주목해야 할 맥락 제공
               - 일반적 표현 대신 구체적 수치/기업명/이슈를 인용
            3. insightCards: 정확히 %d개
               - 각 카드: headline(20자 내외) + body(100~200자) + relatedClusterIndices
               - relatedClusterIndices는 입력 "주요 뉴스 클러스터"의 번호만 사용 (1-based)
               - 각 카드는 서로 다른 테마를 다룰 것 (같은 이슈 중복 금지)
            4. relatedKeywords: 오늘의 핵심 키워드 5~10개 (한글, 중복 없음, 구체적)
               - 예: "반도체", "HBM", "기준금리", "엔비디아"

            주의사항:
            - 입력에 없는 사실을 만들지 말 것
            - 숫자는 입력값을 그대로 사용할 것
            - 특정 종목 매수/매도 권유성 표현 금지

            반드시 아래 JSON 형식으로만 응답하세요:
            {
              "title": "...",
              "summary": "...",
              "insightCards": [
                {"headline": "...", "body": "...", "relatedClusterIndices": [1, 3]}
              ],
              "relatedKeywords": ["...", "..."]
            }
            """.formatted(REQUIRED_INSIGHT_CARDS);

    // ── 데이터 전달용 record ──────────────────────────────────

    /** 프롬프트에 전달할 클러스터 경량 DTO */
    public record ClusterSummary(Long clusterId, String title, String summary) {
    }

    /** 파싱 직후 결과 — 호출자가 relatedClusterIndices를 실제 clusterId로 매핑 */
    public record MarketTrendRawResult(
            String title,
            String summary,
            List<ParsedCard> cards,
            List<String> relatedKeywords
    ) {
        public boolean isEmpty() {
            return (title == null || title.isBlank())
                    || (summary == null || summary.isBlank());
        }
    }

    public record ParsedCard(String headline, String body, List<Integer> relatedClusterIndices) {
        public boolean isValid() {
            return headline != null && !headline.isBlank()
                    && body != null && !body.isBlank();
        }
    }

    // ── OpenAI 요청/응답 DTO ──────────────────────────────────

    record ChatRequest(
            String model,
            List<Message> messages,
            @JsonProperty("max_tokens") int maxTokens,
            double temperature,
            @JsonProperty("response_format") ResponseFormat responseFormat
    ) {
        record Message(String role, String content) {
        }

        record ResponseFormat(String type) {
        }
    }

    record ChatResponse(List<Choice> choices) {
        record Choice(Message message) {
        }

        record Message(String content) {
        }
    }

    record RawResponse(
            String title,
            String summary,
            List<InsightCardRaw> insightCards,
            List<String> relatedKeywords
    ) {
    }

    record InsightCardRaw(
            String headline,
            String body,
            List<Integer> relatedClusterIndices
    ) {
    }

    /** AI 호출/파싱 실패 전용 예외 (재시도 판정용) */
    public static class MarketTrendAiException extends RuntimeException {
        public MarketTrendAiException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
