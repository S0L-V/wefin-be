package com.solv.wefin.domain.news.service;

import com.solv.wefin.domain.news.dto.CollectedNewsDto;
import com.solv.wefin.domain.news.entity.*;
import com.solv.wefin.domain.news.repository.NewsArticleRepository;
import com.solv.wefin.domain.news.repository.RawNewsArticleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.solv.wefin.global.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArticlePersistenceService {

    private final RawNewsArticleRepository rawNewsArticleRepository;
    private final NewsArticleRepository newsArticleRepository;

    /**
     * @return true if the article was saved, false if skipped (duplicate)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean processSingleArticle(CollectedNewsDto dto, NewsSource source, NewsCollectBatch batch) {
        if (rawNewsArticleRepository.existsByOriginalUrl(dto.getOriginalUrl())) {
            log.debug("중복 기사 스킵 - url: {}", dto.getOriginalUrl());
            return false;
        }

        RawNewsArticle rawArticle = RawNewsArticle.of(dto, source, batch);

        try {
            rawNewsArticleRepository.saveAndFlush(rawArticle);
        } catch (DataIntegrityViolationException e) {
            if (isDuplicateOriginalUrlViolation(e)) {
                log.debug("중복 기사 DB 제약 위반 스킵 - url: {}", dto.getOriginalUrl());
                return false;
            }
            log.error("raw 기사 저장 실패 - url: {}", dto.getOriginalUrl(), e);
            throw e;
        }

        normalizeAndSave(rawArticle, dto);
        return true;
    }

    private boolean isDuplicateOriginalUrlViolation(DataIntegrityViolationException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException constraintViolation) {
                if (isOriginalUrlConstraintName(constraintViolation.getConstraintName())) {
                    return true;
                }
                if (refersToOriginalUrlUniqueConstraint(constraintViolation.getSQLException())) {
                    return true;
                }
            } else if (cause instanceof SQLException sqlException
                    && refersToOriginalUrlUniqueConstraint(sqlException)) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private boolean isOriginalUrlConstraintName(String constraintName) {
        return "uk_raw_news_article_original_url".equalsIgnoreCase(constraintName);
    }

    private boolean refersToOriginalUrlUniqueConstraint(SQLException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return false;
        }

        String normalized = message.toLowerCase(Locale.ROOT);
        return normalized.contains("uk_raw_news_article_original_url")
                || (normalized.contains("raw_news_article")
                && normalized.contains("original_url")
                && (normalized.contains("duplicate key")
                || normalized.contains("unique constraint")
                || normalized.contains("unique index")));
    }

    private void normalizeAndSave(RawNewsArticle rawArticle, CollectedNewsDto dto) {
        String dedupKey = generateDedupKey(dto.getOriginalUrl());

        if (newsArticleRepository.existsByDedupKey(dedupKey)) {
            rawArticle.markDuplicated();
            return;
        }

        try {
            NewsArticle article = NewsArticle.of(
                    rawArticle, dto,
                    cleanText(dto.getOriginalTitle()),
                    cleanHtml(dto.getOriginalContent()),
                    detectLanguage(dto.getOriginalTitle()),
                    detectMarketScope(dto.getOriginalTitle()),
                    dedupKey);

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
        return StringUtils.containsKorean(title) ? "KO" : "EN";
    }

    private String detectMarketScope(String title) {
        return StringUtils.containsKorean(title) ? "DOMESTIC" : "GLOBAL";
    }
}
