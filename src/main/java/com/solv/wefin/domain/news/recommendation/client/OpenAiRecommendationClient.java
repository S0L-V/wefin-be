package com.solv.wefin.domain.news.recommendation.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.solv.wefin.domain.game.openai.OpenAiProperties;
import com.solv.wefin.domain.news.recommendation.entity.RecommendedNewsCard.CardType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;

/**
 * 추천 뉴스 카드의 AI 콘텐츠(title, summary, context, linkedClusterIndex)를 생성한다
 *
 * 관심사 1개당 1회 호출하며, 입력으로 관심사 정보 + 관련 클러스터 상위 5개 + 사용자의 전체 관심사 목록을 받아
 * JSON 형태로 카드 콘텐츠를 반환한다
 */
@Slf4j
@Component
public class OpenAiRecommendationClient {

    public static final int MAX_CLUSTERS_IN_PROMPT = 5;

    private final RestClient restClient;
    private final OpenAiProperties properties;
    private final ObjectMapper objectMapper;

    public OpenAiRecommendationClient(
            @Qualifier("openAiRestClient") RestClient restClient,
            OpenAiProperties properties,
            ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * 관심사 기반 추천 카드 콘텐츠를 AI로 생성한다
     *
     * @param cardType         카드 타입 (STOCK/SECTOR)
     * @param interestCode     관심사 코드
     * @param interestName     관심사 표시명
     * @param clusters         관련 클러스터 목록 (최대 5개)
     * @param otherInterests   사용자의 다른 관심사 목록 (맥락 풍부화용)
     * @return 파싱된 카드 콘텐츠
     * @throws RecommendationAiException AI 호출 실패 또는 응답 파싱 실패 시
     */
    public RecommendationCardResult generate(CardType cardType,
                                             String interestCode,
                                             String interestName,
                                             List<ClusterInput> clusters,
                                             List<String> otherInterests) {
        String userMessage = buildUserMessage(cardType, interestCode, interestName,
                clusters, otherInterests);

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
            throw new RecommendationAiException(
                    "OpenAI Recommendation API HTTP 오류: " + e.getStatusCode(), e);
        } catch (Exception e) {
            throw new RecommendationAiException(
                    "OpenAI Recommendation API 호출 실패: " + e.getMessage(), e);
        }

        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw new RecommendationAiException("OpenAI Recommendation API 응답이 비어있습니다", null);
        }

        ChatResponse.Choice choice = response.choices().get(0);
        if (choice == null || choice.message() == null) {
            throw new RecommendationAiException("OpenAI Recommendation API message 누락", null);
        }

        String content = choice.message().content();
        if (content == null || content.isBlank()) {
            throw new RecommendationAiException("OpenAI Recommendation API content 비어있음", null);
        }

        return parse(content, clusters.size());
    }

    private RecommendationCardResult parse(String json, int clusterCount) {
        try {
            RawResponse raw = objectMapper.readValue(json, RawResponse.class);
            if (raw == null || raw.title() == null || raw.summary() == null || raw.context() == null) {
                throw new RecommendationAiException("Recommendation JSON 필수 필드 누락", null);
            }

            int linkedIndex = raw.linkedClusterIndex() != null ? raw.linkedClusterIndex() : 0;
            if (linkedIndex < 0 || linkedIndex >= clusterCount) {
                linkedIndex = 0;
            }

            return new RecommendationCardResult(
                    raw.title(),
                    raw.summary(),
                    raw.context(),
                    linkedIndex
            );
        } catch (JsonProcessingException e) {
            throw new RecommendationAiException(
                    "Recommendation JSON 파싱 실패: " + e.getOriginalMessage(), e);
        }
    }

    private String buildUserMessage(CardType cardType, String interestCode,
                                    String interestName, List<ClusterInput> clusters,
                                    List<String> otherInterests) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 대상 관심사\n");
        sb.append("- 타입: ").append(cardType == CardType.STOCK ? "종목" : "섹터").append("\n");
        sb.append("- 코드: ").append(interestCode).append("\n");
        sb.append("- 이름: ").append(interestName).append("\n\n");

        sb.append("## 관련 뉴스 클러스터 (최신순)\n");
        for (int i = 0; i < clusters.size(); i++) {
            ClusterInput c = clusters.get(i);
            sb.append(i).append(". 제목: ").append(c.title()).append("\n");
            sb.append("   요약: ").append(truncate(c.summary(), 300)).append("\n\n");
        }

        if (!otherInterests.isEmpty()) {
            sb.append("## 사용자의 다른 관심사\n");
            sb.append(String.join(", ", otherInterests)).append("\n");
        }

        return sb.toString();
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }

    // ── 시스템 프롬프트 ────────────────────────────────────────

    private static final String SYSTEM_PROMPT = """
            당신은 한국 금융시장 전문 애널리스트입니다.
            사용자의 관심사(종목 또는 섹터)와 관련된 최근 뉴스 클러스터를 분석하여,
            사용자에게 맞춤형 뉴스 카드 콘텐츠를 작성합니다.

            작성 규칙:
            1. title: 관심사와 연결된 핵심 뉴스 주제 한 줄 요약 (30자 이내, 한글)
            2. summary: 2~3문장으로 관련 뉴스의 핵심 내용을 요약 (100~200자)
               - 구체적인 수치/기업명/이슈를 인용할 것
               - 입력된 클러스터 내용만 참조할 것
            3. context: 이 뉴스가 사용자에게 왜 중요한지 가치 맥락 (80~150자)
               - 사용자의 관심사와 연결하여 "왜 이 뉴스를 봐야 하는지" 설명
               - "보유 관심 종목의...", "관심 분야인 ...에..." 같은 2인칭 표현 사용
               - 사용자의 다른 관심사와의 연관성도 언급하면 좋음
            4. linkedClusterIndex: 가장 대표적인 클러스터 1개의 인덱스 번호 (0-based)
               - 사용자가 가장 관심 가질 만한 클러스터를 선택

            주의사항:
            - 입력에 없는 사실을 만들지 말 것
            - 특정 종목 매수/매도 권유성 표현 금지
            - context는 일반론 금지, 반드시 관심사와 구체적으로 연결할 것

            반드시 아래 JSON 형식으로만 응답:
            {
              "title": "...",
              "summary": "...",
              "context": "...",
              "linkedClusterIndex": 0
            }
            """;

    // ── 데이터 전달용 record ──────────────────────────────────

    /** 프롬프트에 전달할 클러스터 경량 DTO */
    public record ClusterInput(Long clusterId, String title, String summary) {
    }

    /** AI 응답 파싱 결과 */
    public record RecommendationCardResult(
            String title,
            String summary,
            String context,
            int linkedClusterIndex
    ) {
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

    record RawResponse(String title, String summary, String context, Integer linkedClusterIndex) {
    }

    /** AI 호출/파싱 실패 전용 예외 */
    public static class RecommendationAiException extends RuntimeException {
        public RecommendationAiException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
