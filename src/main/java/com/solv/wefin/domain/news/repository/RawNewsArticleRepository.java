package com.solv.wefin.domain.news.repository;

import com.solv.wefin.domain.news.entity.RawNewsArticle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RawNewsArticleRepository extends JpaRepository<RawNewsArticle, Long> {

    boolean existsByOriginalUrlOrExternalArticleId(String originalUrl, String externalArticleId);

    List<RawNewsArticle> findByProcessingStatus(RawNewsArticle.ProcessingStatus processingStatus);
}
