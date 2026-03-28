package com.solv.wefin.domain.news.collector;

import com.solv.wefin.domain.news.dto.CollectedNewsDto;

import java.util.List;

public interface NewsCollector {

    String getSourceName();

    List<CollectedNewsDto> collect(String category);
}
