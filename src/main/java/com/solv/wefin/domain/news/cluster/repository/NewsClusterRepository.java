package com.solv.wefin.domain.news.cluster.repository;

import com.solv.wefin.domain.news.cluster.entity.NewsCluster;
import com.solv.wefin.domain.news.cluster.entity.NewsCluster.ClusterStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NewsClusterRepository extends JpaRepository<NewsCluster, Long> {

    /**
     * 특정 상태의 클러스터를 조회한다.
     * 새 기사 매칭 시 ACTIVE 클러스터 목록 조회에 사용.
     *
     * <p>MVP에서는 ACTIVE 전체를 메모리로 가져와 유사도 비교하는 단순 구조.
     * 클러스터 수가 증가하면 후보군 축소가 필요하다:</p>
     * <ul>
     *     <li>최근 24시간 생성 클러스터만 조회</li>
     *     <li>category/topic 기준 pre-filter</li>
     *     <li>pgvector 기반 nearest neighbor 후보 조회</li>
     * </ul>
     */
    List<NewsCluster> findByStatus(ClusterStatus status);
}
