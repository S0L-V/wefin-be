package com.solv.wefin.domain.news.ingestion.collector;

import com.solv.wefin.domain.news.ingestion.client.dto.CollectedNewsApiResponse;

import java.util.List;

public interface NewsCollector {

    String getSourceName();

    List<CollectedNewsApiResponse> collect(String category);
}
