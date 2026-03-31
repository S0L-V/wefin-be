package com.solv.wefin.domain.news.embedding.service;

import com.solv.wefin.domain.news.embedding.entity.ArticleEmbedding;
import com.solv.wefin.domain.news.embedding.repository.ArticleEmbeddingRepository;
import com.solv.wefin.domain.news.entity.NewsArticle;
import com.solv.wefin.domain.news.repository.NewsArticleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 임베딩 결과를 DB에 반영하는 서비스
 *
 * 외부 API 호출과 트랜잭션을 분리하기 위해 별도 서비스로 구성한다.
 * 정상 흐름은 묶음 트랜잭션으로 처리하고, 실패 건은 개별 REQUIRES_NEW로 격리한다.
 */
@Service
@RequiredArgsConstructor
public class EmbeddingPersistenceService {

    private final ArticleEmbeddingRepository articleEmbeddingRepository;
    private final NewsArticleRepository newsArticleRepository;

    /**
     * 임베딩 대상 기사들의 상태를 PROCESSING으로 일괄 전환한다.
     *
     * @param articles 대상 기사 목록
     */
    @Transactional
    public void markProcessing(List<NewsArticle> articles) {
        for (NewsArticle article : articles) {
            article.markEmbeddingProcessing();
        }
    }

    /**
     * 여러 기사의 임베딩을 한 트랜잭션으로 저장하고 상태를 SUCCESS로 변경한다.
     *
     * @param embeddings 저장할 임베딩 목록
     * @param articles   대상 기사 목록 (상태 변경용)
     */
    @Transactional
    public void saveEmbeddingsBatch(List<ArticleEmbedding> embeddings, List<NewsArticle> articles) {
        articleEmbeddingRepository.saveAll(embeddings);
        for (NewsArticle article : articles) {
            article.markEmbeddingSuccess();
        }
    }

    /**
     * 임베딩 생성 실패를 기록한다. 개별 트랜잭션으로 격리하여 다른 건에 영향을 주지 않는다.
     *
     * @param articleId    실패한 기사 ID
     * @param errorMessage 실패 원인 메시지
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long articleId, String errorMessage) {
        NewsArticle article = newsArticleRepository.findById(articleId)
                .orElseThrow(() -> new IllegalStateException("기사를 찾을 수 없습니다: " + articleId));
        article.markEmbeddingFailed(truncate(errorMessage, 500));
    }

    private String truncate(String text, int maxLength) {
        if (text == null) {
            return "Unknown error";
        }
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }
}
