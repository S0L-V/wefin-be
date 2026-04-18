package com.solv.wefin.domain.news.cluster.entity;

import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 클러스터 요약 섹션의 근거 기사 매핑
 *
 * 각 섹션이 어떤 기사에 근거해서 생성되었는지를 추적한다.
 * RAG 기반 출처 추적 원칙에 따라 모든 AI 결과의 근거를 역추적할 수 있도록 한다.
 */
@Entity
@Table(name = "cluster_summary_section_source")
@IdClass(ClusterSummarySectionSourceId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClusterSummarySectionSource {

    @Id
    @Column(name = "cluster_summary_section_id", nullable = false)
    private Long clusterSummarySectionId;

    @Id
    @Column(name = "news_article_id", nullable = false)
    private Long newsArticleId;

    /**
     * 섹션-기사 출처 매핑을 생성한다
     *
     * @param sectionId 섹션 ID
     * @param articleId 근거 기사 ID
     */
    public static ClusterSummarySectionSource create(Long sectionId, Long articleId) {
        if (sectionId == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        if (articleId == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        ClusterSummarySectionSource source = new ClusterSummarySectionSource();
        source.clusterSummarySectionId = sectionId;
        source.newsArticleId = articleId;
        return source;
    }
}
