package com.solv.wefin.domain.news.tagging.service;

import com.solv.wefin.domain.news.article.entity.NewsArticle;
import com.solv.wefin.domain.news.article.entity.NewsArticleTag;
import com.solv.wefin.domain.news.article.repository.NewsArticleRepository;
import com.solv.wefin.domain.news.article.repository.NewsArticleTagRepository;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 태깅 결과를 DB에 반영하는 서비스
 */
@Service
@RequiredArgsConstructor
public class TaggingPersistenceService {

    private final NewsArticleTagRepository newsArticleTagRepository;
    private final NewsArticleRepository newsArticleRepository;

    /**
     * 태깅 대상 기사들의 상태를 PROCESSING으로 일괄 전환한다.
     */
    @Transactional
    public void markProcessing(List<NewsArticle> articles) {
        for (NewsArticle article : articles) {
            article.markTaggingProcessing();
        }
        newsArticleRepository.saveAll(articles);
    }

    /**
     * 여러 기사의 태그를 한 트랜잭션으로 저장하고 상태를 SUCCESS로 변경한다.
     * 재태깅 대응을 위해 기존 태그를 삭제한 뒤 새로 저장한다.
     */
    @Transactional
    public void saveTagsBatch(List<NewsArticleTag> tags, List<NewsArticle> articles) {
        for (NewsArticle article : articles) {
            newsArticleTagRepository.deleteByNewsArticleId(article.getId());
        }
        newsArticleTagRepository.saveAll(tags);
        for (NewsArticle article : articles) {
            article.markTaggingSuccess();
        }
        newsArticleRepository.saveAll(articles);
    }

    /**
     * 태깅 실패를 기록한다. 개별 트랜잭션으로 격리하여 다른 건에 영향을 주지 않는다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long articleId, String errorMessage) {
        NewsArticle article = newsArticleRepository.findById(articleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TAGGING_ARTICLE_NOT_FOUND));
        article.markTaggingFailed(truncate(errorMessage, 500));
    }

    private String truncate(String text, int maxLength) {
        if (text == null) {
            return "Unknown error";
        }
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }
}
