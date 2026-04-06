package com.solv.wefin.domain.news.summary.service;

import com.solv.wefin.domain.news.cluster.entity.NewsCluster;
import com.solv.wefin.domain.news.cluster.repository.NewsClusterRepository;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 요약 결과를 DB에 반영하는 서비스
 */
@Service
@RequiredArgsConstructor
public class SummaryPersistenceService {

    private final NewsClusterRepository newsClusterRepository;

    /**
     * AI 요약 생성 성공을 반영한다.
     */
    @Transactional
    public void markGenerated(Long clusterId, String title, String summary) {
        NewsCluster cluster = newsClusterRepository.findById(clusterId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SUMMARY_CLUSTER_NOT_FOUND));
        cluster.markSummaryGenerated(title, summary);
    }

    /**
     * AI 요약 생성 실패를 반영한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long clusterId) {
        NewsCluster cluster = newsClusterRepository.findById(clusterId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SUMMARY_CLUSTER_NOT_FOUND));
        cluster.markSummaryFailed();
    }
}
