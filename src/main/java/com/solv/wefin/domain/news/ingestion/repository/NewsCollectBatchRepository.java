package com.solv.wefin.domain.news.ingestion.repository;

import com.solv.wefin.domain.news.ingestion.entity.NewsCollectBatch;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NewsCollectBatchRepository extends JpaRepository<NewsCollectBatch, Long> {
}
