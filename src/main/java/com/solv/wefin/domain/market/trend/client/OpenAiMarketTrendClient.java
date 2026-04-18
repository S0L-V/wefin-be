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
    /** personalized 카드 본문 최대 길이 (overview는 100~200자라 별도) */
    public static final int PERSONALIZED_BODY_MAX_LENGTH = 80;
    /** personalized 카드 조언 박스 최대 길이 */
    public static final int PERSONALIZED_ADVICE_MAX_LENGTH = 100;
    /** personalized 카드 조언 라벨 화이트리스트 (백엔드가 한국어 문자열로 반환) */
    public static final List<String> PERSONALIZED_ADVICE_LABELS = List.of("오늘의 제안", "투자 힌트");

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

    /**
     * 사용자 관심사 기반 맞춤 금융 동향을 생성한다.
     *
     * overview와 달리 title은 생성하지 않고 (그라데이션 박스 헤더는 프론트 고정), 대신
     * 카드별 advice/adviceLabel("오늘의 제안" / "투자 힌트")을 추가로 채워 5개 텍스트 블록
     * (summary + 4 cards 각각의 body/advice)을 한 번의 호출로 반환한다
     */
    public PersonalizedTrendRawResult generatePersonalizedTrend(
            List<MarketSnapshot> snapshots,
            List<ClusterSummary> clusterSummaries,
            List<String> risingStocks,
            List<String> risingTopics,
            List<String> interestStockNames,
            List<String> interestSectorNames,
            List<String> interestTopicNames) {
        String userMessage = buildPersonalizedUserMessage(snapshots, clusterSummaries,
                risingStocks, risingTopics,
                interestStockNames, interestSectorNames, interestTopicNames);

        ChatRequest request = new ChatRequest(
                properties.getModel(),
                List.of(
                        new ChatRequest.Message("system", PERSONALIZED_SYSTEM_PROMPT),
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
                    "OpenAI Personalized Market Trend API HTTP 오류: " + e.getStatusCode(), e);
        } catch (Exception e) {
            throw new MarketTrendAiException(
                    "OpenAI Personalized Market Trend API 호출 실패: " + e.getMessage(), e);
        }

        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw new MarketTrendAiException("OpenAI Personalized Market Trend 응답이 비어있습니다", null);
        }
        ChatResponse.Choice choice = response.choices().get(0);
        if (choice == null || choice.message() == null) {
            throw new MarketTrendAiException("OpenAI Personalized Market Trend message 누락", null);
        }
        String content = choice.message().content();
        if (content == null || content.isBlank()) {
            throw new MarketTrendAiException("OpenAI Personalized Market Trend content 비어있음", null);
        }
        return parsePersonalized(content);
    }

    /**
     * 사용자 관심사와 매칭된 뉴스가 0건일 때 사용하는 시장 액션 브리핑
     *
     * 관심사 컨텍스트는 빼고, 오늘 시장 지표 + 일반 24시간 클러스터를 기반으로 일반 투자자가
     * 어떤 액션을 취하면 좋을지를 카드별 advice로 제공한다.
     */
    public PersonalizedTrendRawResult generateMarketActionBriefing(
            List<MarketSnapshot> snapshots,
            List<ClusterSummary> clusterSummaries,
            List<String> risingStocks,
            List<String> risingTopics) {
        String userMessage = buildUserMessage(snapshots, clusterSummaries, risingStocks, risingTopics);

        ChatRequest request = new ChatRequest(
                properties.getModel(),
                List.of(
                        new ChatRequest.Message("system", MARKET_ACTION_SYSTEM_PROMPT),
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
                    "OpenAI Market Action Briefing HTTP 오류: " + e.getStatusCode(), e);
        } catch (Exception e) {
            throw new MarketTrendAiException(
                    "OpenAI Market Action Briefing 호출 실패: " + e.getMessage(), e);
        }

        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw new MarketTrendAiException("OpenAI Market Action Briefing 응답이 비어있습니다", null);
        }
        ChatResponse.Choice choice = response.choices().get(0);
        if (choice == null || choice.message() == null) {
            throw new MarketTrendAiException("OpenAI Market Action Briefing message 누락", null);
        }
        String content = choice.message().content();
        if (content == null || content.isBlank()) {
            throw new MarketTrendAiException("OpenAI Market Action Briefing content 비어있음", null);
        }
        return parsePersonalized(content);
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

    private String buildPersonalizedUserMessage(List<MarketSnapshot> snapshots,
                                                List<ClusterSummary> clusterSummaries,
                                                List<String> risingStocks,
                                                List<String> risingTopics,
                                                List<String> interestStockNames,
                                                List<String> interestSectorNames,
                                                List<String> interestTopicNames) {
        StringBuilder sb = new StringBuilder();

        sb.append("### 사용자 관심 종목\n");
        sb.append(joinOrPlaceholder(interestStockNames));
        sb.append("\n\n### 사용자 관심 분야\n");
        sb.append(joinOrPlaceholder(interestSectorNames));
        sb.append("\n\n### 사용자 관심 주제\n");
        sb.append(joinOrPlaceholder(interestTopicNames));

        sb.append("\n\n### 시장 지표\n");
        for (MarketSnapshot s : snapshots) {
            sb.append("- ").append(s.getLabel()).append(": ").append(s.getValue());
            if (s.getChangeRate() != null) {
                sb.append(" (").append(s.getChangeDirection()).append(" ").append(s.getChangeRate()).append("%)");
            }
            sb.append("\n");
        }

        sb.append("\n### 사용자 관심사와 매칭된 최근 24시간 뉴스 클러스터\n");
        int limit = Math.min(clusterSummaries.size(), MAX_CLUSTERS_IN_PROMPT);
        for (int i = 0; i < limit; i++) {
            ClusterSummary c = clusterSummaries.get(i);
            sb.append("[").append(i + 1).append("] ").append(c.title());
            if (c.summary() != null && !c.summary().isBlank()) {
                sb.append("\n    요약: ").append(c.summary());
            }
            sb.append("\n");
        }

        sb.append("\n### 떠오르는 종목 (관심사 매칭 클러스터 기준)\n");
        sb.append(truncate(risingStocks, MAX_TAGS_IN_PROMPT).stream().collect(Collectors.joining(", ")));
        sb.append("\n\n### 떠오르는 주제 (관심사 매칭 클러스터 기준)\n");
        sb.append(truncate(risingTopics, MAX_TAGS_IN_PROMPT).stream().collect(Collectors.joining(", ")));

        return sb.toString();
    }

    private static String joinOrPlaceholder(List<String> names) {
        if (names == null || names.isEmpty()) return "(없음)";
        return String.join(", ", names);
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

    private PersonalizedTrendRawResult parsePersonalized(String json) {
        try {
            PersonalizedRawResponse raw = objectMapper.readValue(json, PersonalizedRawResponse.class);
            if (raw == null) {
                throw new MarketTrendAiException("Personalized Market Trend JSON이 비어있습니다", null);
            }

            List<PersonalizedInsightCardRaw> rawCards = raw.insightCards() != null ? raw.insightCards() : List.of();
            List<PersonalizedParsedCard> parsedCards = new ArrayList<>();
            for (PersonalizedInsightCardRaw c : rawCards) {
                if (c == null) continue;
                parsedCards.add(new PersonalizedParsedCard(
                        c.headline(),
                        c.body(),
                        c.advice(),
                        c.adviceLabel(),
                        c.relatedClusterIndices() != null ? c.relatedClusterIndices() : List.of()
                ));
            }

            return new PersonalizedTrendRawResult(
                    raw.summary(),
                    parsedCards,
                    raw.relatedKeywords() != null ? raw.relatedKeywords() : List.of()
            );
        } catch (JsonProcessingException e) {
            throw new MarketTrendAiException("Personalized Market Trend JSON 파싱 실패: " + e.getOriginalMessage(), e);
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

    private static final String PERSONALIZED_SYSTEM_PROMPT = """
            당신은 한국 금융시장 전문 애널리스트로, 특정 사용자에게 맞춤형 동향과 조언을 제공합니다.
            주어진 사용자의 관심 종목/분야/주제, 시장 지표, 그리고 관심사와 매칭된 최근 24시간 뉴스 클러스터를
            바탕으로 사용자 관점의 시장 분석과 카드별 조언을 작성합니다.

            작성 규칙:
            1. summary: 3~4문단 사용자 맞춤 시장 분석 (300~600자)
               - 어투는 2인칭 ("보유 중인 ...", "관심 분야인 ..." 등)
               - 사용자 관심사를 본문에 명시적으로 인용
               - 시장 지표 변동과 관심사를 연결해 설명
            2. insightCards: 정확히 %d개. 각 카드는 사용자 관심사에 직접 연관된 주제만 다룬다.
               각 카드 필드:
               - headline: 20자 내외, "요새 X가 ~ 있어요" 같은 친근한 문체
               - body: %d자 이내, 본문 분석. 구체적 수치/기업명/이슈 인용
               - advice: 60~%d자, **구체적 행동 시사점**. 다음 4요소 중 2개 이상 포함:
                 (a) 어떤 종목/섹터/지표를 봐야 하는지 (이름 명시)
                 (b) 어떤 조건이 충족되면 어떤 신호로 해석해야 하는지
                 (c) 어떤 리스크/체크포인트가 있는지 (구체)
                 (d) 단기/중기 등 시간 프레임
                 ❌ 나쁜 예: "관련 종목의 흐름을 고려해 보세요."
                 ✅ 좋은 예: "보유 중인 삼성전자는 외국인 5거래일 연속 매수가 이어지면 단기 저항선 돌파 가능성. HBM 수요 둔화 지표는 매주 점검 필요."
               - adviceLabel: "오늘의 제안" 또는 "투자 힌트" 중 하나만 사용
                 * "오늘의 제안" — 호재/긍정 흐름 → 비중 확대 검토 등
                 * "투자 힌트" — 변동성/주의 흐름 → 단기 조정 대비 등
               - relatedClusterIndices: 입력 "매칭 클러스터" 번호 (1-based)
            3. relatedKeywords: 사용자 관심사와 직접 관련된 핵심 키워드 5~10개 (한글, 중복 없음)

            주의사항:
            - 입력에 없는 사실/수치를 만들지 말 것
            - 매수/매도 직접 권유 금지 — "비중 확대 검토", "분할 매수 시점 점검", "익절 라인 조정" 같은 분석 표현 사용
            - 관심사와 무관한 종목/섹터를 카드 주제로 삼지 말 것
            - advice는 일반론(예: "전략을 점검해 보세요") 금지. 반드시 구체적 신호/체크포인트/지표명을 포함

            반드시 아래 JSON 형식으로만 응답:
            {
              "summary": "...",
              "insightCards": [
                {
                  "headline": "...",
                  "body": "...",
                  "advice": "...",
                  "adviceLabel": "오늘의 제안",
                  "relatedClusterIndices": [1, 3]
                }
              ],
              "relatedKeywords": ["...", "..."]
            }
            """.formatted(REQUIRED_INSIGHT_CARDS, PERSONALIZED_BODY_MAX_LENGTH, PERSONALIZED_ADVICE_MAX_LENGTH);

    private static final String MARKET_ACTION_SYSTEM_PROMPT = """
            당신은 한국 금융시장 전문 애널리스트입니다. 주어진 시장 지표와 최근 24시간 주요 뉴스 클러스터를
            종합해 오늘 시장 흐름과 일반 투자자가 고려할 만한 액션을 카드별로 제안합니다.
            (사용자의 개별 관심사 정보는 입력되지 않습니다 — 일반 시장 관점에서만 작성하세요)

            작성 규칙:
            1. summary: 3~4문단 오늘 시장 요약 (300~600자)
               - 시장 지표 변동의 핵심 원인과 의미를 해석
               - "오늘 시장이 이러하니 이런 흐름에 주목하세요" 식으로 액션 시사점 명시
               - 어투는 분석가 톤 (3인칭, 정중)
            2. insightCards: 정확히 %d개. 카드는 서로 다른 액션 테마를 다룬다
               (예: 환율 흐름 대응 / 금리 시사점 / 호재 섹터 진입 검토 / 위험 회피 신호 등)
               각 카드 필드:
               - headline: 20자 내외, 액션 지향 ("환율 약세, 수출주에 우호적" 같이)
               - body: %d자 이내, 시장 흐름 분석. 구체적 수치/이슈 인용
               - advice: 60~%d자, **구체적 행동 시사점**. 다음 4요소 중 2개 이상 포함:
                 (a) 봐야 할 구체적 종목/섹터/지표명
                 (b) 어떤 조건이 충족되면 어떤 신호인지
                 (c) 구체적 리스크/체크포인트
                 (d) 단기/중기 시간 프레임
                 ❌ 나쁜 예: "수출 관련 종목의 흐름을 고려해 보세요."
                 ✅ 좋은 예: "원/달러 1450원 이탈 시 자동차·반도체 수출주 단기 모멘텀 약화 가능. 현대차·기아 4월 수출 데이터 발표 후 재평가 추천."
               - adviceLabel: "오늘의 제안" 또는 "투자 힌트" 중 하나만 사용
                 * "오늘의 제안" — 호재/긍정 흐름 기반 액션
                 * "투자 힌트" — 변동성/주의 흐름 기반 액션
               - relatedClusterIndices: 입력 클러스터 번호 (1-based), 카드별 1개 이상
            3. relatedKeywords: 오늘의 핵심 키워드 5~10개 (한글, 중복 없음)

            주의사항:
            - 입력에 없는 사실/수치를 만들지 말 것
            - 매수/매도 직접 권유 금지 — "비중 확대 검토", "분할 매수 시점 점검", "익절 라인 조정" 같은 분석 표현 사용
            - advice는 일반론(예: "전략을 점검해 보세요") 금지. 반드시 구체적 신호/체크포인트/지표명을 포함

            반드시 아래 JSON 형식으로만 응답:
            {
              "summary": "...",
              "insightCards": [
                {
                  "headline": "...",
                  "body": "...",
                  "advice": "...",
                  "adviceLabel": "오늘의 제안",
                  "relatedClusterIndices": [1, 3]
                }
              ],
              "relatedKeywords": ["...", "..."]
            }
            """.formatted(REQUIRED_INSIGHT_CARDS, PERSONALIZED_BODY_MAX_LENGTH, PERSONALIZED_ADVICE_MAX_LENGTH);

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

    /**
     * personalized 응답 — title 없음, 카드별 advice/adviceLabel 추가
     */
    public record PersonalizedTrendRawResult(
            String summary,
            List<PersonalizedParsedCard> cards,
            List<String> relatedKeywords
    ) {
        public boolean isEmpty() {
            return summary == null || summary.isBlank();
        }
    }

    public record PersonalizedParsedCard(
            String headline,
            String body,
            String advice,
            String adviceLabel,
            List<Integer> relatedClusterIndices
    ) {
        public boolean isValid() {
            return headline != null && !headline.isBlank()
                    && body != null && !body.isBlank()
                    && advice != null && !advice.isBlank()
                    && adviceLabel != null && PERSONALIZED_ADVICE_LABELS.contains(adviceLabel);
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

    record PersonalizedRawResponse(
            String summary,
            List<PersonalizedInsightCardRaw> insightCards,
            List<String> relatedKeywords
    ) {
    }

    record PersonalizedInsightCardRaw(
            String headline,
            String body,
            String advice,
            String adviceLabel,
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
