package com.solv.wefin.domain.news.ingestion.service;

import com.solv.wefin.domain.news.ingestion.collector.NewsCollector;
import com.solv.wefin.domain.news.ingestion.client.dto.CollectedNewsApiResponse;
import com.solv.wefin.domain.news.ingestion.entity.NewsCollectBatch;
import com.solv.wefin.domain.news.source.entity.NewsSource;
import com.solv.wefin.domain.news.ingestion.repository.NewsCollectBatchRepository;
import com.solv.wefin.domain.news.source.repository.NewsSourceRepository;
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

        int savedCount = 0;
        int skippedCount = 0;
        int failedCount = 0;

        try {
            List<CollectedNewsApiResponse> articles = collector.collect(category);

            for (CollectedNewsApiResponse dto : articles) {
                try {
                    boolean saved = articlePersistenceService.processSingleArticle(dto, source, batch);
                    if (saved) savedCount++;
                    else skippedCount++;
                } catch (Exception e) {
                    failedCount++;
                    log.warn("기사 처리 실패 - url: {}, error: {}", dto.getOriginalUrl(), e.getMessage());
                }
            }

            batch.success(savedCount, failedCount);
            newsCollectBatchRepository.save(batch);
            log.info("뉴스 수집 배치 완료 - source: {}, saved: {}, skipped: {}, failed: {}",
                    source.getSourceName(), savedCount, skippedCount, failedCount);

        } catch (Exception e) {
            batch.fail(e.getMessage(), savedCount, failedCount);
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
