package com.solv.wefin.domain.news.article.repository;

import com.solv.wefin.domain.news.article.entity.NewsArticleTag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NewsArticleTagRepository extends JpaRepository<NewsArticleTag, Long> {

    List<NewsArticleTag> findByNewsArticleId(Long newsArticleId);
}
