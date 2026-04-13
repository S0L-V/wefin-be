package com.solv.wefin.domain.news.cluster.service;

import com.solv.wefin.domain.news.article.entity.NewsArticle;
import com.solv.wefin.domain.news.cluster.entity.NewsCluster;
import com.solv.wefin.domain.news.cluster.entity.NewsCluster.ClusterStatus;
import com.solv.wefin.domain.news.cluster.entity.NewsClusterArticle;
import lombok.extern.slf4j.Slf4j;
import com.solv.wefin.domain.news.cluster.repository.NewsClusterArticleRepository;
import com.solv.wefin.domain.news.cluster.repository.NewsClusterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 클러스터링 결과를 DB에 반영하는 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClusteringPersistenceService {

    private final NewsClusterRepository clusterRepository;
    private final NewsClusterArticleRepository clusterArticleRepository;

    /**
     * 기존 클러스터에 기사를 추가한다.
     *
     * @param cluster 대상 클러스터
     * @param article 추가할 기사
     * @param articleVector 기사의 대표 벡터
     * @param suspicious suspicious 플래그
     */
    @Transactional
    public void addToCluster(NewsCluster cluster, NewsArticle article,
                             float[] articleVector, boolean suspicious) {
        // 쓰기 락으로 re-fetch. 같은 클러스터에 대한 요약 저장/병합/이상치 제거
        // 경로와 기사 집합 변경을 직렬화하여 CAS 검증 틈을 제거한다
        NewsCluster managed = clusterRepository.findByIdForUpdate(cluster.getId())
                .orElseThrow(() -> new IllegalStateException("클러스터 없음: " + cluster.getId()));

        // 락 대기 중에 병합(loser) 또는 24h 라이프사이클 배치로 INACTIVE 전환될 수 있다.
        // INACTIVE 클러스터에 기사를 추가하면 노출되지 않는 고아 기사가 되므로 중단한다.
        // 바깥 for-loop의 catch에서 이 기사는 스킵되어 다음 배치에서 재매칭된다
        if (managed.getStatus() != ClusterStatus.ACTIVE) {
            log.warn("락 획득 후 클러스터가 INACTIVE → 기사 추가 스킵, clusterId: {}, articleId: {}",
                    managed.getId(), article.getId());
            throw new IllegalStateException(
                    "INACTIVE 클러스터에 기사 추가 불가 — clusterId: " + managed.getId());
        }

        managed.addArticle(
                articleVector,
                article.getId(),
                article.getThumbnailUrl(),
                article.getPublishedAt()
        );

        int order = managed.getArticleCount();
        // 클러스터-기사 매핑 생성
        NewsClusterArticle mapping = NewsClusterArticle.create(
                managed.getId(), article.getId(), order, suspicious);
        clusterArticleRepository.save(mapping);
    }

    /**
     * 새 단독 클러스터를 생성한다.
     *
     * @param article 기사
     * @param articleVector 기사의 대표 벡터
     * @return 생성된 클러스터
     */
    @Transactional
    public NewsCluster createSingleCluster(NewsArticle article, float[] articleVector) {
        NewsCluster cluster = NewsCluster.createSingle(
                articleVector,
                article.getId(),
                article.getThumbnailUrl(),
                article.getPublishedAt()
        );
        clusterRepository.save(cluster);

        NewsClusterArticle mapping = NewsClusterArticle.create(
                cluster.getId(), article.getId(), 1, false);
        clusterArticleRepository.save(mapping);

        return cluster;
    }
}
