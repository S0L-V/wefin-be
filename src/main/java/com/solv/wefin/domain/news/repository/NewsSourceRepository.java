package com.solv.wefin.domain.news.repository;

import com.solv.wefin.domain.news.entity.NewsSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NewsSourceRepository extends JpaRepository<NewsSource, Long> {

    List<NewsSource> findByIsActiveTrue();

    Optional<NewsSource> findBySourceName(String sourceName);
}
