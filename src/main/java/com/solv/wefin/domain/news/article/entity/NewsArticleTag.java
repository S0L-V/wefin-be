package com.solv.wefin.domain.news.article.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "news_article_tag")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NewsArticleTag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "news_article_tag_id")
    private Long id;

    @Column(name = "news_article_id", nullable = false)
    private Long newsArticleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tag_type", nullable = false, length = 30)
    private TagType tagType;

    @Column(name = "tag_code", nullable = false, length = 100)
    private String tagCode;

    @Column(name = "tag_name", nullable = false, length = 100)
    private String tagName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    @Builder
    public NewsArticleTag(Long newsArticleId, TagType tagType, String tagCode, String tagName) {
        this.newsArticleId = newsArticleId;
        this.tagType = tagType;
        this.tagCode = tagCode;
        this.tagName = tagName;
    }

    public enum TagType {
        STOCK, SECTOR, TOPIC;

        private static final java.util.Set<TagType> FILTERABLE = java.util.Set.of(STOCK, SECTOR);

        /**
         * 피드 필터링에 사용 가능한 태그 유형인지 확인한다
         */
        public boolean isFilterable() {
            return FILTERABLE.contains(this);
        }

        /**
         * 문자열을 TagType으로 변환한다. 유효하지 않으면 null을 반환한다
         */
        public static TagType fromStringOrNull(String value) {
            if (value == null) return null;
            try {
                return valueOf(value.toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }
}
