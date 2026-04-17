package com.solv.wefin.domain.game.openai;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Slf4j
@Component
public class OpenAiAnalysisReportClient {

    private final RestClient restClient;
    private final OpenAiProperties properties;
    private final ObjectMapper objectMapper;

    public OpenAiAnalysisReportClient(
            @Qualifier("openAiRestClient") RestClient restClient,
            OpenAiProperties properties,
            ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public AnalysisParts generateReport(AnalysisContext context) {
        String userPrompt = buildPrompt(context);

        log.info("[분석리포트] OpenAI 호출 시작: 기간={}~{}, 턴 {}건",
                context.startDate(), context.endDate(), context.dailyContexts().size());

        OpenAiProperties.Report reportProps = properties.getReport();
        ChatRequest request = new ChatRequest(
                reportProps.getModel(),
                List.of(
                        new ChatRequest.Message("system", SYSTEM_PROMPT),
                        new ChatRequest.Message("user", userPrompt)
                ),
                reportProps.getMaxTokens(),
                reportProps.getTemperature(),
                new ChatRequest.ResponseFormat("json_object")
        );

        ChatResponse response;
        try {
            response = restClient.post()
                    .uri("/v1/chat/completions")
                    .body(request)
                    .retrieve()
                    .body(ChatResponse.class);
        } catch (RestClientException e) {
            log.error("[분석리포트] OpenAI 호출 실패: {}", e.getMessage());
            throw new BusinessException(ErrorCode.ANALYSIS_GENERATION_FAILED);
        }

        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            log.error("[분석리포트] OpenAI 응답 비어있음");
            throw new BusinessException(ErrorCode.ANALYSIS_GENERATION_FAILED);
        }

        String content = response.choices().get(0).message().content();
        if (content == null || content.isBlank()) {
            log.error("[분석리포트] OpenAI content 비어있음");
            throw new BusinessException(ErrorCode.ANALYSIS_GENERATION_FAILED);
        }
        log.info("[분석리포트] OpenAI 호출 완료");
        return parseReportJson(content);
    }

    private AnalysisParts parseReportJson(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            String performance = node.path("performance").asText("");
            String pattern = node.path("pattern").asText("");
            String suggestion = node.path("suggestion").asText("");

            if (performance.isBlank() || pattern.isBlank() || suggestion.isBlank()) {
                log.error("[분석리포트] JSON 필수 필드 누락: {}", truncate(json));
                throw new BusinessException(ErrorCode.ANALYSIS_GENERATION_FAILED);
            }
            return new AnalysisParts(performance, pattern, suggestion);
        } catch (JsonProcessingException e) {
            log.error("[분석리포트] JSON 파싱 실패: {}", truncate(json));
            throw new BusinessException(ErrorCode.ANALYSIS_GENERATION_FAILED);
        }
    }

    private String buildPrompt(AnalysisContext context) {
        StringBuilder daily = new StringBuilder();
        for (DailyContext day : context.dailyContexts()) {
            daily.append("\n[").append(day.turnDate()).append(" 시장 개요]\n");
            daily.append(day.marketOverview()).append("\n");

            if (day.trades().isEmpty()) {
                daily.append("→ 이 날 매매 없음\n");
            } else {
                String tradeSummary = day.trades().stream()
                        .map(t -> String.format("%s %s %d주 @%,d원",
                                t.stockName(),
                                "BUY".equals(t.orderType()) ? "매수" : "매도",
                                t.quantity(),
                                t.price().longValue()))
                        .reduce((a, b) -> a + ", " + b)
                        .orElse("");
                daily.append("→ 이 날 매매: ").append(tradeSummary).append("\n");
            }
        }

        return String.format("""
                아래는 주식 투자 시뮬레이션 게임의 시장 동향과 매매 내역입니다.

                게임 기간: %s ~ %s
                시드머니: %,d원
                최종 자산: %,d원
                수익률: %s%%
                %s

                위의 각 날짜별 시장 동향과 해당 날의 매매를 비교 분석해주세요.
                특히 시장 상황에 비추어 각 매매가 적절했는지 평가해주세요.

                규칙:
                - 한국어로 작성하세요.
                - 이모티콘을 사용하지 마세요.
                - 종목명을 언급할 때는 실제 매매한 종목만 사용하세요.
                - 제공된 시장 동향과 매매 내역에 근거하여 작성하세요.
                - 위에 명시된 게임 기간 이후의 시장 흐름·주가·뉴스는 절대 언급하지 마세요.
                - 종목/산업에 대한 일반적 분석과 투자 원칙(분산 투자, 손절·익절, 매매 빈도 등)은 자유롭게 활용하세요.
                - 일반 원칙을 언급할 때는 이번 게임의 구체적 매매 사례와 연결하여 설명하세요.

                반드시 아래 JSON 형식으로 응답하세요:
                {
                  "performance": "3문장. 전체 수익률에 대한 평가, 매매 빈도/스타일 요약, 두드러진 강점 또는 아쉬움",
                  "pattern": "3~4문장. 시장 동향과 매매 결정을 연결한 패턴 분석. 매매한 종목의 특성이나 산업 맥락을 함께 활용해도 좋습니다.",
                  "suggestion": "2~3문장. 이번 게임의 매매에서 드러난 약점과 연결한 구체적 개선점. 일반 투자 원칙(분산, 손절 등)을 끌어와도 좋되, 어떤 매매에 적용되는지 함께 제시하세요."
                }
                """,
                context.startDate(),
                context.endDate(),
                context.seedMoney().longValue(),
                context.finalAsset().longValue(),
                context.profitRate().toPlainString(),
                daily
        );
    }

    private static final String SYSTEM_PROMPT =
            "당신은 주식 투자 시뮬레이션 게임의 투자 코치 '위피니'입니다. "
                    + "투자자의 매매 내역과 시장 동향을 비교하여, 종목·산업·일반 투자 원칙에 대한 "
                    + "전문 지식을 활용해 유익한 피드백을 제공합니다. 이모티콘을 사용하지 마세요.";

    public record AnalysisContext(
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal seedMoney,
            BigDecimal finalAsset,
            BigDecimal profitRate,
            List<DailyContext> dailyContexts
    ) {}

    public record DailyContext(
            LocalDate turnDate,
            String marketOverview,
            List<TradeSummary> trades
    ) {}

    public record TradeSummary(
            String stockName,
            String orderType,
            int quantity,
            BigDecimal price
    ) {}

    public record AnalysisParts(
            String performance,
            String pattern,
            String suggestion
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

    private static String truncate(String text) {
        if (text == null) return "null";
        return text.length() <= 200 ? text : text.substring(0, 200) + "...(truncated)";
    }

    record ChatResponse(
            List<Choice> choices
    ) {
        record Choice(Message message) {}
        record Message(String content) {}
    }
}
