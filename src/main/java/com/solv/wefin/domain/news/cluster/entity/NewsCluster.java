package com.solv.wefin.domain.news.cluster.entity;

import com.solv.wefin.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 뉴스 클러스터 (유사 기사 그룹)
 */
@Entity
@Table(name = "news_cluster")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NewsCluster extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "news_cluster_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "cluster_type", nullable = false, length = 30)
    private ClusterType clusterType;

    @Column(name = "category", length = 50)
    private String category;

    @Column(name = "topic", length = 100)
    private String topic;

    @Column(name = "topic_label", length = 100)
    private String topicLabel;

    @Column(name = "representative_article_id")
    private Long representativeArticleId;

    @Column(name = "title")
    private String title;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Column(name = "thumbnail_url", columnDefinition = "TEXT")
    private String thumbnailUrl;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ClusterStatus status = ClusterStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(name = "summary_status", nullable = false, length = 30)
    private SummaryStatus summaryStatus = SummaryStatus.PENDING;

    // 클러스터의 중심점, 소속 기사들의 임베딩 벡터를 평균 낸 값
    @Column(name = "centroid_vector", columnDefinition = "float8[]")
    private float[] centroidVector;

    @Column(name = "article_count", nullable = false)
    private int articleCount = 0;

    public enum ClusterStatus {
        ACTIVE, INACTIVE
    }

    public enum ClusterType {
        STOCK, SECTOR, TOPIC, GENERAL
    }

    public enum SummaryStatus {
        PENDING, GENERATED, STALE, FAILED
    }

    @Builder
    private NewsCluster(ClusterType clusterType, float[] centroidVector,
                        Long representativeArticleId, String thumbnailUrl,
                        OffsetDateTime publishedAt) {
        this.clusterType = clusterType;
        this.centroidVector = centroidVector != null ? centroidVector.clone() : null;
        this.representativeArticleId = representativeArticleId;
        this.thumbnailUrl = thumbnailUrl;
        this.publishedAt = publishedAt;
        this.status = ClusterStatus.ACTIVE;
        this.summaryStatus = SummaryStatus.PENDING;
        this.articleCount = 1;
    }

    /**
     * 새 단독 클러스터를 생성한다.
     *
     * @param articleVector 첫 기사의 대표 벡터
     * @param representativeArticleId 대표 기사 ID (최신 기사)
     * @param thumbnailUrl 대표 기사의 썸네일
     * @param publishedAt 대표 기사의 발행 시각
     */
    public static NewsCluster createSingle(float[] articleVector, Long representativeArticleId,
                                           String thumbnailUrl, OffsetDateTime publishedAt) {
        validateVector(articleVector);
        if (representativeArticleId == null) {
            throw new IllegalArgumentException("representativeArticleId는 null일 수 없습니다");
        }

        return NewsCluster.builder()
                .clusterType(ClusterType.GENERAL)
                .centroidVector(articleVector)
                .representativeArticleId(representativeArticleId)
                .thumbnailUrl(thumbnailUrl)
                .publishedAt(publishedAt)
                .build();
    }

    /**
     * 클러스터에 기사를 추가하고 centroid를 점진적으로 업데이트한다.
     *
     * 새 centroid = (기존 centroid * 기존 기사 수 + 새 기사 벡터) / (기존 기사 수 + 1)
     *
     * @param articleVector 추가되는 기사의 대표 벡터
     * @param articleId 추가되는 기사 ID
     * @param thumbnailUrl 기사의 썸네일 (대표 기사 갱신 시 사용)
     * @param articlePublishedAt 기사의 발행 시각
     */
    public void addArticle(float[] articleVector, Long articleId,
                           String thumbnailUrl, OffsetDateTime articlePublishedAt) {
        validateVector(articleVector);
        if (articleId == null) {
            throw new IllegalArgumentException("articleId는 null일 수 없습니다");
        }

        updateCentroid(articleVector);
        this.articleCount++;

        // 대표 기사 갱신: 최신 기사가 대표
        if (articlePublishedAt != null && (this.publishedAt == null || articlePublishedAt.isAfter(this.publishedAt))) {
            this.representativeArticleId = articleId;
            this.publishedAt = articlePublishedAt;
            if (thumbnailUrl != null && !thumbnailUrl.isBlank()) {
                this.thumbnailUrl = thumbnailUrl;
            }
        }

        // AI 요약 재생성 필요
        if (this.summaryStatus == SummaryStatus.GENERATED) {
            this.summaryStatus = SummaryStatus.STALE;
        }
    }

    /**
     * centroid를 점진적으로 재계산한다.
     */
    private static void validateVector(float[] vector) {
        if (vector == null || vector.length == 0) {
            throw new IllegalArgumentException("벡터가 null이거나 비어있습니다");
        }
    }

    private void updateCentroid(float[] newVector) {
        if (this.centroidVector == null) {
            this.centroidVector = newVector.clone();
            return;
        }

        if (this.centroidVector.length != newVector.length) {
            throw new IllegalStateException(
                    "벡터 차원 불일치: centroid=" + this.centroidVector.length + ", new=" + newVector.length);
        }

        float[] updated = new float[this.centroidVector.length];
        for (int i = 0; i < updated.length; i++) {
            updated[i] = (this.centroidVector[i] * this.articleCount + newVector[i]) / (this.articleCount + 1);
        }
        this.centroidVector = updated;
    }

    /**
     * 24시간 경과 시 비활성화한다.
     * 피드에서 제외되지만 데이터는 보존된다.
     */
    public void deactivate() {
        this.status = ClusterStatus.INACTIVE;
    }

    /**
     * AI 요약 생성 완료를 기록한다.
     */
    public void markSummaryGenerated(String title, String summary) {
        this.title = title;
        this.summary = summary;
        this.summaryStatus = SummaryStatus.GENERATED;
    }

    /**
     * AI 요약 생성 실패를 기록한다.
     * 다음 요약 배치에서 재시도 대상이 된다.
     */
    public void markSummaryFailed() {
        this.summaryStatus = SummaryStatus.FAILED;
    }

    /**
     * 이상치 제거 후 클러스터 집계 상태를 재계산한다.
     *
     * @param newArticleCount 남은 기사 수
     * @param newCentroid 재계산된 centroid (남은 기사 없으면 null)
     * @param newRepresentativeArticleId 새 대표 기사 ID (null이면 변경 안 함)
     * @param newPublishedAt 새 대표 기사 발행 시각
     * @param newThumbnailUrl 새 대표 기사 썸네일
     */
    public void recalculateAfterOutlierRemoval(int newArticleCount, float[] newCentroid,
                                                Long newRepresentativeArticleId,
                                                OffsetDateTime newPublishedAt,
                                                String newThumbnailUrl) {
        this.articleCount = newArticleCount;
        this.centroidVector = newCentroid != null ? newCentroid.clone() : null;

        if (newRepresentativeArticleId != null) {
            this.representativeArticleId = newRepresentativeArticleId;
            this.publishedAt = newPublishedAt;
            if (newThumbnailUrl != null && !newThumbnailUrl.isBlank()) {
                this.thumbnailUrl = newThumbnailUrl;
            }
        }
    }
}
