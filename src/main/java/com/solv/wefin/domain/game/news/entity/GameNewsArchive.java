package com.solv.wefin.domain.game.news.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor(access =  AccessLevel.PROTECTED)
@Entity
@Table(name = "game_news_archive")
public class GameNewsArchive {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "news_id")
    private UUID newsId;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "summary", nullable = false, columnDefinition = "TEXT")
    private String summary;

    @Column(name = "source", nullable = false, length = 100)
    private String source;

    @Column(name = "original_url", nullable = false, length = 1000, unique = true)
    private String originalUrl;

    @Column(name = "published_at", nullable = false)
    private OffsetDateTime publishedAt;

    @Column(name = "category", length = 50)
    private String category;

    @Column(name = "keyword", length = 100)
    private String keyword;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public static GameNewsArchive create(String title, String summary, String source, String originalUrl,
                                         OffsetDateTime publishedAt, String category, String keyword) {
        GameNewsArchive archive = new GameNewsArchive();
        archive.title = title;
        archive.summary = summary;
        archive.source = source;
        archive.originalUrl = originalUrl;
        archive.publishedAt = publishedAt;
        archive.category = category;
        archive.keyword = keyword;
        archive.createdAt = OffsetDateTime.now();
        return archive;
    }
}
