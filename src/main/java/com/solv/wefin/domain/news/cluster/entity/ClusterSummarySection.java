package com.solv.wefin.domain.news.cluster.entity;

import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;

/**
 * 클러스터 요약의 개별 섹션 (소제목 + 단락)
 *
 * 뉴스 상세 페이지에서 AI 요약을 섹션형으로 보여주기 위해 사용한다.
 * 각 섹션은 heading(소제목)과 body(본문)로 구성되며,
 * ClusterSummarySectionSource를 통해 근거 기사를 추적할 수 있다.
 */
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "cluster_summary_section",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_cluster_summary_section_cluster_order",
                columnNames = {"news_cluster_id", "section_order"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClusterSummarySection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cluster_summary_section_id")
    private Long id;

    @Column(name = "news_cluster_id", nullable = false)
    private Long newsClusterId;

    @Column(name = "section_order", nullable = false)
    private int sectionOrder;

    @Column(name = "heading", nullable = false, columnDefinition = "TEXT")
    private String heading;

    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    private String body;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Builder
    private ClusterSummarySection(Long newsClusterId, int sectionOrder,
                                  String heading, String body) {
        this.newsClusterId = newsClusterId;
        this.sectionOrder = sectionOrder;
        this.heading = heading;
        this.body = body;
    }

    /**
     * 클러스터 요약 섹션을 생성한다
     *
     * @param clusterId 소속 클러스터 ID
     * @param order 섹션 순서 (0부터 시작)
     * @param heading 소제목
     * @param body 본문 단락
     */
    public static ClusterSummarySection create(Long clusterId, int order,
                                               String heading, String body) {
        if (clusterId == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        if (heading == null || heading.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        if (body == null || body.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        return ClusterSummarySection.builder()
                .newsClusterId(clusterId)
                .sectionOrder(order)
                .heading(heading)
                .body(body)
                .build();
    }
}
