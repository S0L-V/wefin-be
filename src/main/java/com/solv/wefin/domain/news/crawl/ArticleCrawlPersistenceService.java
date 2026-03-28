package com.solv.wefin.domain.news.crawl;

import com.solv.wefin.domain.news.entity.NewsArticle;
import com.solv.wefin.domain.news.repository.NewsArticleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 크롤링 결과를 DB에 반영하는 서비스
 */
@Service
@RequiredArgsConstructor
public class ArticleCrawlPersistenceService {

    private final NewsArticleRepository newsArticleRepository;

    /**
     * 크롤링 성공 결과를 저장한다. 본문과 썸네일을 업데이트하고 상태를 SUCCESS로 변경한다.
     *
     * @param articleId 대상 기사 ID
     * @param content 크롤링된 본문 텍스트
     * @param thumbnailUrl og:image에서 추출한 썸네일 URL (없으면 null)
     */
    @Transactional
    public void saveCrawlSuccess(Long articleId, String content, String thumbnailUrl) {
        NewsArticle article = newsArticleRepository.findById(articleId)
                .orElseThrow(() -> new IllegalStateException("기사를 찾을 수 없습니다: " + articleId));
        article.updateCrawledContent(content, thumbnailUrl);
    }

    /**
     * 크롤링 실패를 기록한다. 상태를 FAILED로 변경하고 retryCount를 증가시킨다.
     *
     * @param articleId 대상 기사 ID
     * @param errorMessage 실패 원인 메시지
     */
    @Transactional
    public void saveCrawlFailure(Long articleId, String errorMessage) {
        NewsArticle article = newsArticleRepository.findById(articleId)
                .orElseThrow(() -> new IllegalStateException("기사를 찾을 수 없습니다: " + articleId));
        article.markCrawlFailed(errorMessage);
    }
}
