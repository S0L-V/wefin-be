package com.solv.wefin.domain.news.repository;

import com.solv.wefin.domain.news.entity.NewsCollectBatch;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NewsCollectBatchRepository extends JpaRepository<NewsCollectBatch, Long> {
}
