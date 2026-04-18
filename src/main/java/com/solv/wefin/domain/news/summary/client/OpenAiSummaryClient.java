package com.solv.wefin.domain.news.summary.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.solv.wefin.domain.news.config.dto.OpenAiChatApiResponse;
import lombok.extern.slf4j.Slf4j;
import com.solv.wefin.domain.news.summary.dto.SummaryResult;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * OpenAI Chat Completions API를 호출하여 클러스터 요약을 생성한다.
 *
 * 한 클러스터에 묶인 여러 기사를 종합하여 하나의 title + summary 브리핑을 만든다.
 * 프롬프트에서 팩트, 분석, 전망, 영향 등을 다각도에서 정리하도록 유도한다.
 */
@Slf4j
@Component
public class OpenAiSummaryClient {
    private static final String OPENAI_CHAT_URL = "https://api.openai.com/v1/chat/completions";

    private static final int MAX_ARTICLE_LENGTH = 2000;

    // 클러스터당 프롬프트에 포함할 최대 기사 수
    private static final int MAX_ARTICLES_PER_CLUSTER = 10;

    private static final String SINGLE_ARTICLE_SUMMARY_PROMPT = """
            당신은 금융 뉴스 전문 에디터입니다.
            기사 한 건의 본문을 읽고 핵심 내용을 요약하세요.

            규칙:
            1. title: 핵심 이슈를 한 줄로 요약 (50자 이내, 한글)
            2. leadSummary: 기사 핵심 요약 (200~400자, 한글)
               - 팩트, 원인/배경, 전망/영향을 포함하되 기사에 있는 내용만 작성
               - 구체적 수치, 기업명, 날짜를 포함할 것
               - 광고, 기자 서명, 언론사 홍보 문구 등은 제외
            3. sections: 없음 (빈 배열)

            반드시 아래 JSON 형식으로만 응답하세요:
            {
              "title": "요약 제목",
              "leadSummary": "핵심 내용 요약...",
              "sections": []
            }
            """;

    private static final String SINGLE_TITLE_PROMPT = """
            당신은 금융 뉴스 전문 에디터입니다.
            기사 한 건의 제목을 깔끔하게 다듬어주세요.

            규칙:
            1. 50자 이내 한글로 작성
            2. 언론사 코너명([경제D톡스], [기자수첩] 등)이나 특수 기호(①②③ 등)는 제거
            3. 핵심 내용만 남기되, 원본의 의미를 훼손하지 말 것
            4. 말줄임표로 잘린 문장이면 본문을 참고하여 완결된 문장으로 작성

            반드시 아래 JSON 형식으로만 응답하세요:
            {
              "title": "다듬어진 제목"
            }
            """;

    private static final String SYSTEM_PROMPT = """
            당신은 금융 뉴스 전문 에디터입니다.
            여러 관련 기사를 종합하여 하나의 브리핑을 작성하세요.
            각 기사에는 [1], [2] 등의 번호가 매겨져 있습니다.

            작성 규칙:
            1. title: 핵심 이슈를 한 줄로 요약 (50자 이내, 한글)
            2. leadSummary: 여러 기사를 종합한 리드 요약 (200~400자, 한글)
               - 가능하면 다음 구조를 포함:
                 · 팩트: 무슨 일이 일어났는가
                 · 분석/원인: 왜 일어났는가
                 · 전망: 앞으로 어떻게 될 것인가
                 · 영향/파급: 투자자에게 어떤 의미인가
               - 모든 항목이 없어도 괜찮음. 기사에 있는 내용만 작성
               - 개별 기사를 나열하지 말고 하나의 스토리로 엮을 것
            3. sections: 상세 분석 섹션 배열 (2~4개 권장, 기사 수에 따라 유연하게)
               - 각 섹션은 서로 다른 역할을 담당해야 한다. 아래 중 적합한 것을 선택:
                 · 무슨 일이 있었나 (What happened)
                 · 왜 일어났나 (Why / 원인과 메커니즘)
                 · 어떤 영향이 있나 (Impact / 시장·투자자 영향)
                 · 앞으로 어떻게 되나 (Outlook / 전망)
               - 섹션 간 내용이 중복되지 않도록 할 것
               - heading: 소제목 (20자 이내, 한글)
               - body: 해당 논점에 대한 상세 설명 (100~200자)
                 · 구체적 수치, 기업명, 날짜를 포함할 것
                 · "신중 필요", "영향 가능" 같은 일반적 표현 대신 구체적 근거를 제시
               - sourceArticleIndices: 이 섹션의 근거가 된 기사 번호 배열 (예: [1, 3])
                 · 반드시 입력 기사의 번호만 사용할 것
                 · 각 섹션은 가능하면 서로 다른 기사를 인용할 것 (같은 기사만 반복 금지)
                 · 기사에 직접 언급된 내용만 근거로 사용하고 추론하지 말 것
            4. suggestedQuestions: 추천 질문 배열 (정확히 3개)
               - 브리핑을 읽은 사용자가 추가로 궁금해할 만한 질문
               - 기사에서 다루지 않은 후속 이슈, 영향, 전망 중심
               - 한글 의문형 문장 ("~할까?", "~일까?", "~될까?" 등)
               - 너무 일반적인 질문 금지 (예: "앞으로 어떻게 될까?")
               - 구체적 기업명, 수치, 정책명 등을 포함할 것

            주의사항:
            - 기사에 없는 인과관계를 만들지 말 것 (예: 무관한 기업/사건 연결 금지)
            - 숫자(주가, 환율, 비율)는 기사 원문의 값을 그대로 사용할 것

            반드시 아래 JSON 형식으로만 응답하세요:
            {
              "title": "엔비디아 급락, 반도체주 동반 하락",
              "leadSummary": "미국 증시에서 엔비디아 주가가 10% 넘게 급락하며...",
              "sections": [
                {
                  "heading": "실적 부진이 촉발한 매도세",
                  "body": "엔비디아의 2분기 실적이 시장 기대를 하회하면서...",
                  "sourceArticleIndices": [1, 2]
                },
                {
                  "heading": "반도체 업종 전반으로 확산",
                  "body": "엔비디아 급락 여파로 삼성전자, SK하이닉스 등...",
                  "sourceArticleIndices": [2, 3]
                }
              ],
              "suggestedQuestions": [
                "엔비디아 실적 부진이 국내 반도체 수출에 미치는 영향은?",
                "SK하이닉스의 HBM 매출 비중 변화가 주가에 어떤 의미일까?",
                "반도체 업종 조정이 ETF 투자자에게 매수 기회가 될 수 있을까?"
              ]
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

        OpenAiChatApiResponse response;
        try {
            response = restTemplate.postForObject(OPENAI_CHAT_URL, request, OpenAiChatApiResponse.class);
        } catch (HttpStatusCodeException e) {
            throw new OpenAiClientException(
                    "OpenAI Summary API HTTP 오류: " + e.getStatusCode(),
                    e.getStatusCode(), e);
        } catch (RestClientException e) {
            throw new OpenAiClientException(
                    "OpenAI Summary API 호출 실패: " + e.getMessage(),
                    null, e);
        }

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
            sb.append("[").append(i + 1).append("] ");
            sb.append(truncated).append("\n\n");
        }

        return sb.toString();
    }

    /**
     * 단독 클러스터용 — 기사 한 건의 title을 AI로 정제한다.
     *
     * <p>규칙 기반 클렌징으로 처리 불가한 경우(너무 짧은 제목 등)의 fallback으로 사용된다.
     * summary는 건드리지 않고 title만 재생성한다.</p>
     *
     * @param originalTitle 원본 제목
     * @param content 기사 본문 (제목이 잘린 경우 본문 참고용)
     * @return 정제된 title. 실패 시 null.
     */
    public String generateSingleTitle(String originalTitle, String content) {
        try {
            String truncatedContent = content != null && content.length() > MAX_ARTICLE_LENGTH
                    ? content.substring(0, MAX_ARTICLE_LENGTH) : (content != null ? content : "");
            String userMessage = "제목: " + originalTitle + "\n\n본문: " + truncatedContent;

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            Map<String, Object> body = Map.of(
                    "model", model,
                    "response_format", Map.of("type", "json_object"),
                    "messages", List.of(
                            Map.of("role", "system", "content", SINGLE_TITLE_PROMPT),
                            Map.of("role", "user", "content", userMessage)
                    )
            );

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            OpenAiChatApiResponse response;
            try {
                response = restTemplate.postForObject(OPENAI_CHAT_URL, request, OpenAiChatApiResponse.class);
            } catch (HttpStatusCodeException e) {
                throw new OpenAiClientException("OpenAI Single Title API HTTP 오류: " + e.getStatusCode(), e.getStatusCode(), e);
            } catch (RestClientException e) {
                throw new OpenAiClientException("OpenAI Single Title API 호출 실패: " + e.getMessage(), null, e);
            }

            if (response == null || response.getChoices() == null || response.getChoices().isEmpty()) {
                return null;
            }

            OpenAiChatApiResponse.Choice firstChoice = response.getChoices().get(0);
            if (firstChoice == null || firstChoice.getMessage() == null || firstChoice.getMessage().getContent() == null) {
                return null;
            }

            SummaryResult result = parseSummaryResult(firstChoice.getMessage().getContent());
            String title = result.getTitle();
            if (title == null || title.isBlank()) {
                return null;
            }
            return title.trim();
        } catch (OpenAiClientException e) {
            throw e;
        } catch (Exception e) {
            log.warn("단독 title AI 재생성 실패: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 단독 클러스터용 — 기사 한 건의 본문을 AI로 요약한다.
     *
     * 크롤링된 원문에는 광고, 기자 서명 등 노이즈가 포함되어 있으므로
     * AI가 핵심 내용만 추출하여 title + leadSummary를 생성한다
     *
     * @param title 기사 제목
     * @param content 기사 본문
     * @return title + leadSummary (sections는 빈 배열)
     */
    public SummaryResult generateSingleArticleSummary(String title, String content) {
        String truncatedContent = content != null && content.length() > MAX_ARTICLE_LENGTH
                ? content.substring(0, MAX_ARTICLE_LENGTH) : (content != null ? content : "");
        String safeTitle = title != null ? title : "";
        String userMessage = "제목: " + safeTitle + "\n\n본문: " + truncatedContent;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        Map<String, Object> body = Map.of(
                "model", model,
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of("role", "system", "content", SINGLE_ARTICLE_SUMMARY_PROMPT),
                        Map.of("role", "user", "content", userMessage)
                )
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        OpenAiChatApiResponse response;
        try {
            response = restTemplate.postForObject(OPENAI_CHAT_URL, request, OpenAiChatApiResponse.class);
        } catch (HttpStatusCodeException e) {
            throw new OpenAiClientException(
                    "OpenAI Single Summary API HTTP 오류: " + e.getStatusCode(), e.getStatusCode(), e);
        } catch (RestClientException e) {
            throw new OpenAiClientException(
                    "OpenAI Single Summary API 호출 실패: " + e.getMessage(), null, e);
        }

        if (response == null || response.getChoices() == null || response.getChoices().isEmpty()) {
            throw new IllegalStateException("OpenAI Single Summary API 응답이 비어있습니다");
        }

        OpenAiChatApiResponse.Choice firstChoice = response.getChoices().get(0);
        if (firstChoice == null || firstChoice.getMessage() == null || firstChoice.getMessage().getContent() == null) {
            throw new IllegalStateException("OpenAI Single Summary API 응답 메시지가 비어있습니다");
        }

        return parseSummaryResult(firstChoice.getMessage().getContent());
    }

    private static final String QUESTION_PROMPT = """
            당신은 금융 뉴스 전문 에디터입니다.
            아래 뉴스 브리핑을 읽은 사용자가 추가로 궁금해할 만한 질문 3개를 생성하세요.

            규칙:
            1. 정확히 3개의 질문을 생성
            2. 기사에서 다루지 않은 후속 이슈, 영향, 전망 중심
            3. 한글 의문형 문장 ("~할까?", "~일까?" 등)
            4. 구체적 기업명, 수치, 정책명 등을 포함할 것
            5. "앞으로 어떻게 될까?" 같은 일반적 질문 금지

            반드시 아래 JSON 형식으로만 응답하세요:
            {
              "suggestedQuestions": ["질문1", "질문2", "질문3"]
            }
            """;

    /**
     * 단독 클러스터용 — 제목 + 요약을 기반으로 추천 질문 3개를 생성한다
     *
     * 다건 클러스터는 요약 프롬프트에서 질문을 함께 생성하지만,
     * 단독 클러스터는 요약 흐름이 다르므로 별도 AI 호출이 필요하다.
     *
     * 에러 분류
     * - 재시도 가능 (HTTP 429/5xx, 네트워크 오류): OpenAiClientException 전파 →
     *   호출자가 이번 실행 주기 클러스터를 skip하거나 markFailed하여 다음 배치에서 재시도
     * - 재시도 불가 (응답 비어있음, JSON 파싱 실패): 빈 리스트 반환. 요약은 정상 저장
     *
     * @param title 클러스터 제목
     * @param summary 클러스터 요약
     * @return 추천 질문 목록 (응답 이상/파싱 실패 시 빈 리스트)
     * @throws OpenAiClientException HTTP 오류 또는 네트워크 오류 (재시도 대상)
     */
    public List<String> generateQuestions(String title, String summary) {
        String safeTitle = title != null ? title : "";
        String safeSummary = summary != null ? summary : "";
        String userMessage = "제목: " + safeTitle + "\n\n요약: " + safeSummary;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        Map<String, Object> body = Map.of(
                "model", model,
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of("role", "system", "content", QUESTION_PROMPT),
                        Map.of("role", "user", "content", userMessage)
                )
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        OpenAiChatApiResponse response;
        try {
            response = restTemplate.postForObject(OPENAI_CHAT_URL, request, OpenAiChatApiResponse.class);
        } catch (HttpStatusCodeException e) {
            throw new OpenAiClientException(
                    "OpenAI Questions API HTTP 오류: " + e.getStatusCode(), e.getStatusCode(), e);
        } catch (RestClientException e) {
            throw new OpenAiClientException(
                    "OpenAI Questions API 호출 실패: " + e.getMessage(), null, e);
        }

        // 응답 구조 이상은 재시도해도 같은 결과일 확률이 높아 빈 리스트로 처리
        if (response == null || response.getChoices() == null || response.getChoices().isEmpty()) {
            log.warn("OpenAI Questions API 응답 비어있음 — title: {}", title);
            return List.of();
        }

        OpenAiChatApiResponse.Choice firstChoice = response.getChoices().get(0);
        if (firstChoice == null || firstChoice.getMessage() == null || firstChoice.getMessage().getContent() == null) {
            log.warn("OpenAI Questions API 메시지 비어있음 — title: {}", title);
            return List.of();
        }

        try {
            SummaryResult parsed = parseSummaryResult(firstChoice.getMessage().getContent());
            return parsed.hasQuestions() ? parsed.getSuggestedQuestions() : List.of();
        } catch (Exception e) {
            log.warn("추천 질문 JSON 파싱 실패 — title: {}", title, e);
            return List.of();
        }
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
