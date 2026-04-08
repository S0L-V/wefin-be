package com.solv.wefin.domain.game.news.crawler;

import java.time.OffsetDateTime;

public record CrawledArticle(
        String title,
        String summary,
        String source,
        String originalUrl,
        OffsetDateTime publishedAt,
        String category,
        String keyword
) {}
