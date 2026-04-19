package com.solv.wefin.domain.news.recommendation.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.solv.wefin.domain.news.recommendation.client.OpenAiRecommendationClient.RecommendationAiException;
import com.solv.wefin.domain.news.recommendation.client.OpenAiRecommendationClient.RecommendationCardResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * OpenAI 응답 JSON 파싱 로직을 단독 테스트한다
 */
class OpenAiRecommendationClientParseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("정상 JSON 응답을 올바르게 파싱한다")
    void parse_validJson_returnsResult() throws Exception {
        String json = """
                {
                  "title": "반도체 업황 회복",
                  "summary": "삼성전자와 SK하이닉스의 실적 개선 전망",
                  "context": "보유 관심 종목의 실적에 긍정적 영향",
                  "linkedClusterIndex": 2
                }
                """;

        RecommendationCardResult result = invokeParse(json, 5);

        assertThat(result.title()).isEqualTo("반도체 업황 회복");
        assertThat(result.summary()).contains("삼성전자");
        assertThat(result.context()).contains("긍정적");
        assertThat(result.linkedClusterIndex()).isEqualTo(2);
    }

    @Test
    @DisplayName("linkedClusterIndex가 범위를 초과하면 0으로 fallback한다")
    void parse_indexOutOfRange_fallbackToZero() throws Exception {
        String json = """
                {
                  "title": "제목",
                  "summary": "요약",
                  "context": "맥락",
                  "linkedClusterIndex": 99
                }
                """;

        RecommendationCardResult result = invokeParse(json, 3);

        assertThat(result.linkedClusterIndex()).isEqualTo(0);
    }

    @Test
    @DisplayName("linkedClusterIndex가 없으면 0으로 fallback한다")
    void parse_noIndex_fallbackToZero() throws Exception {
        String json = """
                {
                  "title": "제목",
                  "summary": "요약",
                  "context": "맥락"
                }
                """;

        RecommendationCardResult result = invokeParse(json, 3);

        assertThat(result.linkedClusterIndex()).isEqualTo(0);
    }

    @Test
    @DisplayName("필수 필드(title) 누락 시 RecommendationAiException 발생")
    void parse_missingTitle_throws() {
        String json = """
                {
                  "summary": "요약",
                  "context": "맥락"
                }
                """;

        assertThatThrownBy(() -> invokeParse(json, 3))
                .isInstanceOf(RecommendationAiException.class);
    }

    @Test
    @DisplayName("유효하지 않은 JSON 시 RecommendationAiException 발생")
    void parse_invalidJson_throws() {
        String json = "이것은 JSON이 아닙니다";

        assertThatThrownBy(() -> invokeParse(json, 3))
                .isInstanceOf(RecommendationAiException.class);
    }

    private RecommendationCardResult invokeParse(String json, int clusterCount) throws Exception {
        OpenAiRecommendationClient client = new OpenAiRecommendationClient(null, null, objectMapper);
        Method parse = OpenAiRecommendationClient.class.getDeclaredMethod("parse", String.class, int.class);
        parse.setAccessible(true);
        try {
            return (RecommendationCardResult) parse.invoke(client, json, clusterCount);
        } catch (java.lang.reflect.InvocationTargetException e) {
            if (e.getCause() instanceof RecommendationAiException rae) throw rae;
            throw e;
        }
    }
}
