package com.solv.wefin.domain.news.article.repository;

import com.solv.wefin.domain.news.article.entity.NewsArticleTag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NewsArticleTagRepository extends JpaRepository<NewsArticleTag, Long> {

    List<NewsArticleTag> findByNewsArticleId(Long newsArticleId);

    /**
     * 여러 기사의 태그를 한 번에 조회한다.
     */
    List<NewsArticleTag> findByNewsArticleIdIn(List<Long> newsArticleIds);

    /**
     * 특정 기사의 태그를 전부 삭제한다. 재태깅 시 기존 태그 정리에 사용한다.
     */
    void deleteByNewsArticleId(Long newsArticleId);
}
