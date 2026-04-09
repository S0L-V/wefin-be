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
     * 여러 기사의 특정 타입 태그만 조회한다.
     * 피드에서 STOCK 태그만 필요할 때 전체 조회 후 Java 필터링 대신 DB에서 필터링.
     */
    List<NewsArticleTag> findByNewsArticleIdInAndTagType(List<Long> newsArticleIds, NewsArticleTag.TagType tagType);

    /**
     * 특정 기사의 태그를 전부 삭제한다. 재태깅 시 기존 태그 정리에 사용한다.
     */
    void deleteByNewsArticleId(Long newsArticleId);
}
