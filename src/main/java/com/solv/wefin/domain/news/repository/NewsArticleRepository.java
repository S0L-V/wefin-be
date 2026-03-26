package com.solv.wefin.domain.news.repository;

import com.solv.wefin.domain.news.entity.NewsArticle;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NewsArticleRepository extends JpaRepository<NewsArticle, Long> {

    boolean existsByDedupKey(String dedupKey);
}
