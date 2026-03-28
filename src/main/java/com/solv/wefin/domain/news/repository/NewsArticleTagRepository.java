package com.solv.wefin.domain.news.repository;

import com.solv.wefin.domain.news.entity.NewsArticleTag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NewsArticleTagRepository extends JpaRepository<NewsArticleTag, Long> {

    List<NewsArticleTag> findByNewsArticleId(Long newsArticleId);
}
