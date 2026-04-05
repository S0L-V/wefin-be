package com.solv.wefin.domain.news.summary.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.solv.wefin.domain.news.config.dto.OpenAiChatApiResponse;
import com.solv.wefin.domain.news.summary.dto.SummaryResult;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * OpenAI Chat Completions API를 호출하여 클러스터 요약을 생성한다.
 *
 * 한 클러스터에 묶인 여러 기사를 종합하여 하나의 title + summary 브리핑을 만든다.
 * 프롬프트에서 팩트, 분석, 전망, 영향 등을 다각도에서 정리하도록 유도한다.
 */
@Component
public class OpenAiSummaryClient {
    private static final String OPENAI_CHAT_URL = "https://api.openai.com/v1/chat/completions";

    private static final int MAX_ARTICLE_LENGTH = 2000;

    // 클러스터당 프롬프트에 포함할 최대 기사 수
    private static final int MAX_ARTICLES_PER_CLUSTER = 10;

    private static final String SYSTEM_PROMPT = """
            당신은 금융 뉴스 전문 에디터입니다.
            여러 관련 기사를 종합하여 하나의 브리핑을 작성하세요.
            
            작성 규칙:
            1. title: 핵심 이슈를 한 줄로 요약 (50자 이내, 한글)
            2. summary: 여러 기사를 종합한 브리핑 (200~400자, 한글)
               - 가능하면 다음 구조를 포함:
                 · 팩트: 무슨 일이 일어났는가
                 · 분석/원인: 왜 일어났는가
                 · 전망: 앞으로 어떻게 될 것인가
                 · 영향/파급: 투자자에게 어떤 의미인가
               - 모든 항목이 없어도 괜찮음. 기사에 있는 내용만 작성
               - 개별 기사를 나열하지 말고 하나의 스토리로 엮을 것
            
            반드시 아래 JSON 형식으로만 응답하세요:
            {
              "title": "엔비디아 급락, 반도체주 동반 하락",
              "summary": "미국 증시에서 엔비디아 주가가 10% 넘게 급락하며..."
            }
            """;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;

    public OpenAiSummaryClient(@Qualifier("summaryRestTemplate") RestTemplate restTemplate,
                               ObjectMapper objectMapper,
                               @Value("${openai.api-key}") String apiKey,
                               @Value("${openai.summary.model}") String model) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.model = model;
    }

    /**
     * 클러스터 소속 기사들을 종합하여 title + summary를 생성한다.
     *
     * @param articles 기사 목록 ("제목: ...\n본문: ...")
     * @return 생성된 title + summary
     */
    public SummaryResult generateSummary(List<String> articles) {
        if (articles == null || articles.isEmpty()) {
            throw new IllegalArgumentException("articles는 null이거나 비어있을 수 없습니다");
        }

        // 기사 목록을 하나의 user 메시지로 직렬화
        // 기사 경계를 "--- 기사 N ---"으로 구분
        String userMessage = buildUserMessage(articles);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        /**
         *  response_format=json_object:
         *  → 모델이 반드시 유효한 JSON만 반환하도록 강제
         *
         *  messages 구성:
         *  → system: 역할/작성 규칙 정의
         *  → user: 기사 원문 전달
         */
        Map<String, Object> body = Map.of(
                "model", model,
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user", "content", userMessage)
                )
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        OpenAiChatApiResponse response = restTemplate.postForObject(OPENAI_CHAT_URL, request, OpenAiChatApiResponse.class);

        if (response == null || response.getChoices() == null || response.getChoices().isEmpty()) {
            throw new IllegalStateException("OpenAI Summary API 응답이 비어있습니다");
        }

        OpenAiChatApiResponse.Choice firstChoice = response.getChoices().get(0);
        if (firstChoice == null) {
            throw new IllegalStateException("OpenAI Summary API 첫 번째 choice가 비어있습니다");
        }

        OpenAiChatApiResponse.Message message = firstChoice.getMessage();
        if (message == null || message.getContent() == null) {
            throw new IllegalStateException("OpenAI Summary API 응답 메시지가 비어있습니다");
        }

        // JSON 문자열인 content를 SummaryResult로 변환
        return parseSummaryResult(message.getContent());
    }

    /**
     * 기사 목록을 OpenAI user 메시지 한 건으로 직렬화한다.
     *
     * 기사당 {@link #MAX_ARTICLE_LENGTH}자 + 클러스터당 최대 {@link #MAX_ARTICLES_PER_CLUSTER}건의
     * 이중 상한을 적용하여 프롬프트 크기가 컨텍스트 한도를 넘지 않도록 보장한다.
     */
    private String buildUserMessage(List<String> articles) {
        int limit = Math.min(articles.size(), MAX_ARTICLES_PER_CLUSTER);
        StringBuilder sb = new StringBuilder();
        sb.append("아래 ").append(limit).append("건의 관련 기사를 종합하여 브리핑을 작성하세요.\n\n");

        for (int i = 0; i < limit; i++) {
            String article = articles.get(i) != null ? articles.get(i) : "";
            String truncated = article.length() > MAX_ARTICLE_LENGTH
                    ? article.substring(0, MAX_ARTICLE_LENGTH) : article;
            sb.append("--- 기사 ").append(i + 1).append(" ---\n");
            sb.append(truncated).append("\n\n");
        }

        return sb.toString();
    }

    /**
     * LLM이 반환한 JSON 문자열(content)을 SummaryResult DTO로 역직렬화한다.
     */
    private SummaryResult parseSummaryResult(String json) {
        try {
            return objectMapper.readValue(json, SummaryResult.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("요약 결과 JSON 파싱 실패: " + e.getMessage(), e);
        }
    }

}
