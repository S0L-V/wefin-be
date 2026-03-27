package com.solv.wefin.domain.news.service;

import com.solv.wefin.domain.news.dto.CollectedNewsDto;
import com.solv.wefin.domain.news.entity.*;
import com.solv.wefin.domain.news.repository.NewsArticleRepository;
import com.solv.wefin.domain.news.repository.RawNewsArticleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArticlePersistenceService {

    private final RawNewsArticleRepository rawNewsArticleRepository;
    private final NewsArticleRepository newsArticleRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processSingleArticle(CollectedNewsDto dto, NewsSource source, NewsCollectBatch batch) {
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

    private String generateDedupKey(String url) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(url.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return "url:" + hex;
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
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
