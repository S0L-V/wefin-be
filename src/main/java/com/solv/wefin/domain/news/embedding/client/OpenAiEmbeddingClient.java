package com.solv.wefin.domain.news.embedding.client;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
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
 * OpenAI Embedding API를 호출하여 텍스트를 벡터로 변환한다.
 * 배열 입력을 지원하므로, 기사 1건의 청크들을 한 번에 요청할 수 있다.
 */
@Slf4j
@Component
public class OpenAiEmbeddingClient {

    private static final String OPENAI_EMBEDDING_URL = "https://api.openai.com/v1/embeddings";

    private final RestTemplate restTemplate;
    private final String apiKey;
    private final String model;

    public OpenAiEmbeddingClient(@Qualifier("embeddingRestTemplate") RestTemplate restTemplate,
                                 @Value("${openai.api-key}") String apiKey,
                                 @Value("${openai.embedding.model}") String model) {
        this.restTemplate = restTemplate;
        this.apiKey = apiKey;
        this.model = model;
    }

    /**
     * 여러 텍스트를 한 번에 임베딩으로 변환한다.
     *
     * @param texts 임베딩할 텍스트 목록
     * @return 입력 순서와 동일한 순서의 임베딩 벡터 목록
     */
    public List<float[]> getEmbeddings(List<String> texts) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        Map<String, Object> body = Map.of(
                "model", model,
                "input", texts
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        EmbeddingResponse response = restTemplate.postForObject(
                OPENAI_EMBEDDING_URL, request, EmbeddingResponse.class);

        if (response == null || response.getData() == null) {
            throw new IllegalStateException("OpenAI Embedding API 응답이 비어있습니다");
        }

        return response.getData().stream()
                .sorted((a, b) -> Integer.compare(a.getIndex(), b.getIndex()))
                .map(EmbeddingData::getEmbedding)
                .toList();
    }

    @Getter
    private static class EmbeddingResponse {
        private List<EmbeddingData> data;
    }

    @Getter
    private static class EmbeddingData {
        private int index;
        private float[] embedding;
    }
}
