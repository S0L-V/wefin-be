package com.solv.wefin.domain.news.embedding.repository;

import com.solv.wefin.domain.news.embedding.entity.ArticleEmbedding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ArticleEmbeddingRepository extends JpaRepository<ArticleEmbedding, Long> {

    /**
     * 특정 기사의 특정 모델로 생성된 청크별 임베딩을 조회한다.
     *
     * @param newsArticleId 기사 ID
     * @param embeddingModel 임베딩 모델명 (예: "text-embedding-3-small")
     * @return 해당 기사의 청크별 임베딩 목록
     */
    List<ArticleEmbedding> findByNewsArticleIdAndEmbeddingModel(Long newsArticleId, String embeddingModel);

    /**
     * 특정 기사의 특정 모델로 생성된 임베딩을 전부 삭제한다.
     * 모델 교체나 재임베딩 시 기존 데이터를 정리하기 위해 사용한다.
     *
     * @param newsArticleId 기사 ID
     * @param embeddingModel 임베딩 모델명
     */
    void deleteByNewsArticleIdAndEmbeddingModel(Long newsArticleId, String embeddingModel);

    /**
     * 특정 기사에 대해 특정 모델의 임베딩이 존재하는지 확인한다.
     * 중복 생성 방지 시 사용한다.
     *
     * @param newsArticleId 기사 ID
     * @param embeddingModel 임베딩 모델명
     * @return 임베딩이 존재하면 true
     */
    boolean existsByNewsArticleIdAndEmbeddingModel(Long newsArticleId, String embeddingModel);
}
