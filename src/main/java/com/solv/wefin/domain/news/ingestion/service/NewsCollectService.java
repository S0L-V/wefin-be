package com.solv.wefin.domain.news.ingestion.service;

import com.solv.wefin.domain.news.ingestion.collector.NewsCollector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NewsCollectService {

    private final List<NewsCollector> newsCollectors;
    private final NewsSourceCollectService newsSourceCollectService;

    public void collectAll() {
        for (NewsCollector collector : newsCollectors) {
            newsSourceCollectService.collectFromSource(collector, null);
        }
    }
}
