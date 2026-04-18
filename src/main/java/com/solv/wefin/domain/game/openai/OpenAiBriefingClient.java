package com.solv.wefin.domain.game.openai;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
@Component
public class OpenAiBriefingClient {

    private final RestClient restClient;
    private final OpenAiProperties properties;
    private final ObjectMapper objectMapper;

    public OpenAiBriefingClient(
            @Qualifier("openAiRestClient") RestClient restClient,
            OpenAiProperties properties,
            ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.properties = properties;
        this.objectMapper = objectMapper;

        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            log.warn("[OpenAI] API 키가 설정되지 않았습니다. 브리핑 생성이 실패합니다.");
        }
    }

    /**
     * 뉴스 기사 목록을 받아 OpenAI API로 시장 브리핑을 생성한다.
     * 뉴스 Entity에 의존하지 않고 제목/요약/URL/카테고리만 받는다.
     *
     * @return 생성된 브리핑 텍스트, 실패 시 빈 Optional 대신 예외를 던진다.
     *         (폴백 처리는 호출하는 Service에서 ��당)
     */
    public BriefingParts generateBriefing(LocalDate date, List<ArticleSummary> articles) {
        String newsContext = buildNewsContext(articles);
        String userPrompt = buildPrompt(date, newsContext);

        log.info("[브리핑] OpenAI 호출 시작: date={}, 뉴스 {}건", date, articles.size());

        ChatRequest request = new ChatRequest(
                properties.getModel(),
                List.of(
                        new ChatRequest.Message("system", SYSTEM_PROMPT),
                        new ChatRequest.Message("user", userPrompt)
                ),
                properties.getMaxTokens(),
                properties.getTemperature(),
                new ChatRequest.ResponseFormat("json_object")
        );

        ChatResponse response = restClient.post()
                .uri("/v1/chat/completions")
                .body(request)
                .retrieve()
                .body(ChatResponse.class);

        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw new IllegalStateException("OpenAI API 응답이 비어 있습니다");
        }

        String content = response.choices().get(0).message().content();
        log.info("[브리핑] OpenAI 호출 완료: date={}", date);
        return parseBriefingJson(content);
    }

    private BriefingParts parseBriefingJson(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            String marketOverview = node.path("marketOverview").asText("");
            String keyIssues = node.path("keyIssues").asText("");
            String investmentHint = node.path("investmentHint").asText("");

            if (marketOverview.isBlank() || keyIssues.isBlank() || investmentHint.isBlank()) {
                throw new IllegalStateException("OpenAI JSON 응답에 필수 필드가 비어 있습니다: " + json);
            }

            return new BriefingParts(marketOverview, keyIssues, investmentHint);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("OpenAI JSON 파싱 실패: " + json, e);
        }
    }

    // ── 내부 메서드 ──────────────────────────────────────────

    private String buildNewsContext(List<ArticleSummary> articles) {
        List<ArticleSummary> limited = articles.stream().limit(30).toList();

        return IntStream.range(0, limited.size())
                .mapToObj(i -> {
                    ArticleSummary a = limited.get(i);
                    boolean hasSummary = a.summary() != null
                            && !a.summary().isBlank()
                            && !a.summary().equals(a.title());
                    return "[" + a.category() + "] 제목: " + a.title()
                            + (hasSummary ? "\n    내용: " + a.summary() : "");
                })
                .collect(Collectors.joining("\n"));
    }

    private String buildPrompt(LocalDate date, String newsContext) {
        return String.format("""
                아래는 특정 날짜의 금융 뉴스 기사들입니다.

                [뉴스 기사]
                %s

                규칙:
                - 위 기사들의 내용만 근거로 분석하세요.
                - 기사에 없는 내용을 추측하거나 덧붙이지 마세요.
                - 당신이 알고 있는 사전 지식은 절대 사용하지 마세요.
                - URL이나 링크를 응답에 절대 포함하지 마세요.
                - 날짜, 연도, 월, 일을 응답에 절대 포함하지 마세요. ("2021년", "올해", "1월" 등 금지)
                - 번호 표기("기사 1" 등)는 절대 사용하지 마세요.
                - 이모티콘을 사용하지 마세요.

                반드시 아래 JSON 형식으로 응답하세요:
                {
                  "marketOverview": "5~7문장, 그 날 가장 중요한 섹터 2~3개를 중심으로 구체적 수치와 함께 상세히 분석",
                  "keyIssues": "- 가장 영향력 있는 섹터/이슈\\n- 두 번째 영향력 있는 섹터/이슈\\n- 세 번째 영향력 있는 섹터/이슈",
                  "investmentHint": "1~2문장, 특정 종목명 언급 금지"
                }

                주요 이슈는 기사들을 종합 분석하여 시장에 가장 영향력이 큰 3개만 선별하세요.
                """,
                newsContext
        );
    }

    // ── 상수 ────────────────────────────────────────────────

    private static final String SYSTEM_PROMPT =
            "당신은 주식 투자 시뮬레이션 게임의 시장 동향 해설자 '위피니'입니다. "
                    + "제공된 뉴스 기사 내용만을 근거로 시장 동향을 분석하세요. "
                    + "당신의 사전 학습 지식이나 기사에 없는 정보는 절대 사용하지 마세요. "
                    + "이모티콘을 사용하지 마세요.";

    // ── OpenAI API 요청/응답 DTO ────────────────────────────

    /**
     * 뉴스 Entity에 의존하지 않기 위한 경량 DTO.
     * Service에서 Entity → ArticleSummary로 변환해서 전달한다.
     */
    public record ArticleSummary(
            String title,
            String summary,
            String url,
            String category
    ) {}

    /** OpenAI JSON 응답을 파싱한 3파트 브리핑 결과. */
    public record BriefingParts(
            String marketOverview,
            String keyIssues,
            String investmentHint
    ) {}

    record ChatRequest(
            String model,
            List<Message> messages,
            @JsonProperty("max_tokens") int maxTokens,
            double temperature,
            @JsonProperty("response_format") ResponseFormat responseFormat
    ) {
        record Message(String role, String content) {}
        record ResponseFormat(String type) {}
    }

    record ChatResponse(
            List<Choice> choices
    ) {
        record Choice(Message message) {}
        record Message(String content) {}
    }
}
