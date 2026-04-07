package com.solv.wefin.domain.news.tagging.service;

import com.solv.wefin.domain.news.article.entity.NewsArticle;
import com.solv.wefin.domain.news.article.entity.NewsArticle.RelevanceStatus;
import com.solv.wefin.domain.news.article.repository.NewsArticleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 기사 relevance 저장 전용 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RelevancePersistenceService {

    private final NewsArticleRepository newsArticleRepository;

    /**
     * 기사 관련성을 저장한다.
     * 대상 기사가 존재하지 않으면 false를 반환하여 호출자가 "실제 저장 실패"를 구분할 수 있게 한다
     * (silent success 방지).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean saveRelevance(Long articleId, RelevanceStatus relevance) {
        Optional<NewsArticle> article = newsArticleRepository.findById(articleId);
        if (article.isEmpty()) {
            log.warn("relevance 저장 대상 기사 없음 — articleId: {}", articleId);
            return false;
        }
        article.get().updateRelevance(relevance);
        return true;
    }
}
