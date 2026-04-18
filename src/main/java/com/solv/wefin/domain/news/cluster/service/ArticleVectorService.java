package com.solv.wefin.domain.news.cluster.service;

import com.solv.wefin.domain.news.embedding.entity.ArticleEmbedding;
import com.solv.wefin.domain.news.embedding.repository.ArticleEmbeddingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 기사별 대표 벡터를 계산하는 서비스
 */
@Service
@RequiredArgsConstructor
public class ArticleVectorService {

    private final ArticleEmbeddingRepository articleEmbeddingRepository;

    @Value("${openai.embedding.model:text-embedding-3-small}")
    private String embeddingModel;

    /**
     * 기사의 대표 벡터를 계산한다.
     * 해당 기사의 모든 청크 임베딩을 조회하여 평균 벡터를 반환한다.
     *
     * @param newsArticleId 기사 ID
     * @return 대표 벡터 (1536차원), 임베딩이 없으면 null
     */
    public float[] calculateRepresentativeVector(Long newsArticleId) {
        if (newsArticleId == null) {
            throw new IllegalArgumentException("newsArticleId는 null일 수 없습니다");
        }

        List<ArticleEmbedding> embeddings = articleEmbeddingRepository
                .findByNewsArticleIdAndEmbeddingModel(newsArticleId, embeddingModel);

        if (embeddings.isEmpty()) {
            return null;
        }

        return averageVectors(embeddings);
    }

    /**
     * 청크 임베딩들의 단순 평균 벡터를 계산한다.
     */
    private float[] averageVectors(List<ArticleEmbedding> embeddings) {
        float[] firstVector = embeddings.get(0).getEmbedding();
        if (firstVector == null || firstVector.length == 0) {
            throw new IllegalArgumentException("첫 번째 청크의 임베딩이 null이거나 비어있습니다");
        }

        int dimension = firstVector.length;
        float[] sum = new float[dimension];

        for (int idx = 0; idx < embeddings.size(); idx++) {
            float[] vector = embeddings.get(idx).getEmbedding();
            if (vector == null || vector.length != dimension) {
                throw new IllegalArgumentException(
                        "청크 임베딩 차원 불일치 - index: " + idx
                                + ", expected: " + dimension
                                + ", actual: " + (vector == null ? "null" : vector.length));
            }
            for (int i = 0; i < dimension; i++) {
                sum[i] += vector[i];
            }
        }

        float count = embeddings.size();
        float[] average = new float[dimension];
        for (int i = 0; i < dimension; i++) {
            average[i] = sum[i] / count;
        }

        return average;
    }
}
