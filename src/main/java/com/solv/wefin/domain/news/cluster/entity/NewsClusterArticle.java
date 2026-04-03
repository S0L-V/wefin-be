package com.solv.wefin.domain.news.cluster.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;

/**
 * 뉴스 클러스터 - 기사 매핑
 *
 * 하나의 기사가 하나의 클러스터에 속하는 관계를 관리한다.
 * 대표 기사 여부는 NewsCluster.representativeArticleId로 단일 관리한다.
 *
 * BaseEntity를 상속하지 않는 이유: 매핑 테이블은 생성/삭제만 수행하고
 * 수정이 없어서 updatedAt이 불필요. createdAt만 JPA Auditing으로 관리한다.
 */
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "news_cluster_article",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_news_cluster_article_cluster_article",
                columnNames = {"news_cluster_id", "news_article_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NewsClusterArticle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "news_cluster_article_id")
    private Long id;

    @Column(name = "news_cluster_id", nullable = false)
    private Long newsClusterId;

    @Column(name = "news_article_id", nullable = false)
    private Long newsArticleId;

    /**
     * V1 레거시 컬럼. 대표 기사 판단은 NewsCluster.representativeArticleId를 사용한다.
     * DB NOT NULL 제약이 있어 기본값 false로 유지.
     */
    @Column(name = "is_representative", nullable = false)
    private boolean isRepresentative = false;

    @Column(name = "article_order", nullable = false)
    private int articleOrder;

    @Column(name = "suspicious", nullable = false)
    private boolean suspicious;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Builder
    private NewsClusterArticle(Long newsClusterId, Long newsArticleId,
                               int articleOrder, boolean suspicious) {
        this.newsClusterId = newsClusterId;
        this.newsArticleId = newsArticleId;
        this.isRepresentative = false;
        this.articleOrder = articleOrder;
        this.suspicious = suspicious;
    }

    /**
     * 클러스터에 기사 매핑을 생성한다.
     *
     * @param clusterId 클러스터 ID
     * @param articleId 기사 ID
     * @param order 정렬 순서
     * @param suspicious 품질 의심 여부 (soft scoring 60~80점)
     */
    public static NewsClusterArticle create(Long clusterId, Long articleId,
                                            int order, boolean suspicious) {
        if (clusterId == null) {
            throw new IllegalArgumentException("clusterId는 null일 수 없습니다");
        }
        if (articleId == null) {
            throw new IllegalArgumentException("articleId는 null일 수 없습니다");
        }

        return NewsClusterArticle.builder()
                .newsClusterId(clusterId)
                .newsArticleId(articleId)
                .articleOrder(order)
                .suspicious(suspicious)
                .build();
    }
}
