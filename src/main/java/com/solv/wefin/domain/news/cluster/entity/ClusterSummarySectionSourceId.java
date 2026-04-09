package com.solv.wefin.domain.news.cluster.entity;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * ClusterSummarySectionSource 복합키
 */
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class ClusterSummarySectionSourceId implements Serializable {

    private Long clusterSummarySectionId;
    private Long newsArticleId;
}
