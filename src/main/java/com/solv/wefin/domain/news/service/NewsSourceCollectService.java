package com.solv.wefin.domain.news.service;

import com.solv.wefin.domain.news.collector.NewsCollector;
import com.solv.wefin.domain.news.dto.CollectedNewsDto;
import com.solv.wefin.domain.news.entity.NewsCollectBatch;
import com.solv.wefin.domain.news.entity.NewsSource;
import com.solv.wefin.domain.news.repository.NewsCollectBatchRepository;
import com.solv.wefin.domain.news.repository.NewsSourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NewsSourceCollectService {

    private final NewsSourceRepository newsSourceRepository;
    private final NewsCollectBatchRepository newsCollectBatchRepository;
    private final ArticlePersistenceService articlePersistenceService;

    public void collectFromSource(NewsCollector collector, String category) {
        NewsSource source = getOrCreateSource(collector);
        if (!source.getIsActive()) {
            log.info("비활성 소스 스킵: {}", source.getSourceName());
            return;
        }

        // Each article is persisted in REQUIRES_NEW, so the batch row must be committed first.
        NewsCollectBatch batch = NewsCollectBatch.builder()
                .newsSource(source)
                .requestedCategory(category)
                .build();
        batch = newsCollectBatchRepository.saveAndFlush(batch);

        int collectedCount = 0;
        int failedCount = 0;

        try {
            List<CollectedNewsDto> articles = collector.collect(category);

            for (CollectedNewsDto dto : articles) {
                try {
                    articlePersistenceService.processSingleArticle(dto, source, batch);
                    collectedCount++;
                } catch (Exception e) {
                    failedCount++;
                    log.warn("기사 처리 실패 - url: {}, error: {}", dto.getOriginalUrl(), e.getMessage());
                }
            }

            batch.success(collectedCount, failedCount);
            newsCollectBatchRepository.save(batch);
            log.info("뉴스 수집 배치 완료 - source: {}, collected: {}, failed: {}",
                    source.getSourceName(), collectedCount, failedCount);

        } catch (Exception e) {
            batch.fail(e.getMessage(), collectedCount, failedCount);
            newsCollectBatchRepository.save(batch);
            log.error("뉴스 수집 배치 실패 - source: {}, error: {}", source.getSourceName(), e.getMessage());
        }
    }

    private NewsSource getOrCreateSource(NewsCollector collector) {
        return newsSourceRepository.findBySourceName(collector.getSourceName())
                .orElseGet(() -> {
                    try {
                        return newsSourceRepository.save(
                                NewsSource.builder()
                                        .sourceName(collector.getSourceName())
                                        .sourceType(NewsSource.SourceType.API)
                                        .isActive(true)
                                        .build()
                        );
                    } catch (DataIntegrityViolationException e) {
                        return newsSourceRepository.findBySourceName(collector.getSourceName())
                                .orElseThrow(() -> e);
                    }
                });
    }
}
