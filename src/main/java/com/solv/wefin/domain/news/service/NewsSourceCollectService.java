package com.solv.wefin.domain.news.service;

import com.solv.wefin.domain.news.collector.NewsCollector;
import com.solv.wefin.domain.news.dto.CollectedNewsDto;
import com.solv.wefin.domain.news.entity.*;
import com.solv.wefin.domain.news.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NewsSourceCollectService {

    private final NewsSourceRepository newsSourceRepository;
    private final NewsCollectBatchRepository newsCollectBatchRepository;
    private final RawNewsArticleRepository rawNewsArticleRepository;
    private final NewsArticleRepository newsArticleRepository;

    @Transactional
    public void collectFromSource(NewsCollector collector, String category) {
        NewsSource source = getOrCreateSource(collector);
        if (!source.getIsActive()) {
            log.info("비활성 소스 스킵: {}", source.getSourceName());
            return;
        }

        NewsCollectBatch batch = NewsCollectBatch.builder()
                .newsSource(source)
                .requestedCategory(category)
                .build();
        newsCollectBatchRepository.save(batch);

        int collectedCount = 0;
        int failedCount = 0;

        try {
            List<CollectedNewsDto> articles = collector.collect(category);

            for (CollectedNewsDto dto : articles) {
                try {
                    processArticle(dto, source, batch);
                    collectedCount++;
                } catch (Exception e) {
                    failedCount++;
                    log.warn("기사 처리 실패 - url: {}, error: {}", dto.getOriginalUrl(), e.getMessage());
                }
            }

            batch.success(collectedCount, failedCount);
            log.info("뉴스 수집 배치 완료 - source: {}, collected: {}, failed: {}",
                    source.getSourceName(), collectedCount, failedCount);

        } catch (Exception e) {
            batch.fail(e.getMessage(), collectedCount, failedCount);
            log.error("뉴스 수집 배치 실패 - source: {}, error: {}", source.getSourceName(), e.getMessage());
        }
    }

    private void processArticle(CollectedNewsDto dto, NewsSource source, NewsCollectBatch batch) {
        if (rawNewsArticleRepository.existsByOriginalUrlOrExternalArticleId(
                dto.getOriginalUrl(), dto.getExternalArticleId())) {
            log.debug("중복 기사 스킵 - url: {}", dto.getOriginalUrl());
            return;
        }

        RawNewsArticle rawArticle = RawNewsArticle.builder()
                .newsSource(source)
                .newsCollectBatch(batch)
                .externalArticleId(dto.getExternalArticleId())
                .originalUrl(dto.getOriginalUrl())
                .originalTitle(dto.getOriginalTitle())
                .originalContent(dto.getOriginalContent())
                .originalThumbnailUrl(dto.getOriginalThumbnailUrl())
                .originalPublishedAt(dto.getOriginalPublishedAt())
                .rawPayload(dto.getRawPayload())
                .build();

        try {
            rawNewsArticleRepository.save(rawArticle);
        } catch (DataIntegrityViolationException e) {
            log.debug("중복 기사 DB 제약 위반 스킵 - url: {}", dto.getOriginalUrl());
            return;
        }

        normalizeAndSave(rawArticle, dto);
    }

    private void normalizeAndSave(RawNewsArticle rawArticle, CollectedNewsDto dto) {
        String dedupKey = generateDedupKey(dto.getOriginalUrl());

        if (newsArticleRepository.existsByDedupKey(dedupKey)) {
            rawArticle.markDuplicated();
            return;
        }

        try {
            NewsArticle article = NewsArticle.builder()
                    .rawNewsArticleId(rawArticle.getId())
                    .publisherName(dto.getPublisherName())
                    .title(cleanText(dto.getOriginalTitle()))
                    .content(cleanHtml(dto.getOriginalContent()))
                    .originalUrl(dto.getOriginalUrl())
                    .thumbnailUrl(dto.getOriginalThumbnailUrl())
                    .publishedAt(dto.getOriginalPublishedAt())
                    .languageCode(detectLanguage(dto.getOriginalTitle()))
                    .marketScope(detectMarketScope(dto.getOriginalTitle()))
                    .dedupKey(dedupKey)
                    .collectedAt(rawArticle.getCollectedAt())
                    .build();

            newsArticleRepository.save(article);
            rawArticle.markNormalized();

            log.debug("기사 정제 완료 - id: {}, title: {}", article.getId(), article.getTitle());

        } catch (Exception e) {
            rawArticle.markFailed();
            throw e;
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

    private String generateDedupKey(String url) {
        return "url:" + Math.abs(url.hashCode());
    }

    private String cleanText(String text) {
        if (text == null) return null;
        return text.trim();
    }

    private String cleanHtml(String content) {
        if (content == null) return null;
        return content
                .replaceAll("<[^>]+>", "")
                .replaceAll("&[a-zA-Z]+;", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String detectLanguage(String title) {
        if (title == null) return "EN";
        boolean hasKorean = title.chars().anyMatch(c -> Character.UnicodeScript.of(c) == Character.UnicodeScript.HANGUL);
        return hasKorean ? "KO" : "EN";
    }

    private String detectMarketScope(String title) {
        if (title == null) return "GLOBAL";
        boolean hasKorean = title.chars().anyMatch(c -> Character.UnicodeScript.of(c) == Character.UnicodeScript.HANGUL);
        return hasKorean ? "DOMESTIC" : "GLOBAL";
    }
}
