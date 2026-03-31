package com.solv.wefin.domain.news.ingestion.repository;

import com.solv.wefin.domain.news.ingestion.entity.RawNewsArticle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RawNewsArticleRepository extends JpaRepository<RawNewsArticle, Long> {

    boolean existsByOriginalUrl(String originalUrl);

    List<RawNewsArticle> findByProcessingStatus(RawNewsArticle.ProcessingStatus processingStatus);
}
