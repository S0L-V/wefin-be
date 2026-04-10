package com.solv.wefin.domain.news.article.service;

import com.solv.wefin.domain.news.article.entity.NewsArticleTag.TagType;
import com.solv.wefin.domain.news.article.repository.NewsArticleTagRepository;
import com.solv.wefin.domain.news.article.repository.NewsArticleTagRepository.PopularTagProjection;
import com.solv.wefin.domain.news.cluster.entity.NewsCluster.ClusterStatus;
import com.solv.wefin.domain.news.cluster.entity.NewsCluster.SummaryStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 뉴스 기사 태그 조회 서비스
 *
 * ACTIVE 클러스터에 속한 기사들의 인기 태그를 조회한다.
 * limit이 0이면 전체를 반환한다
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NewsTagQueryService {

    private static final int MAX_LIMIT = 500;
    private static final List<SummaryStatus> VISIBLE_STATUSES =
            List.of(SummaryStatus.GENERATED, SummaryStatus.STALE);

    private final NewsArticleTagRepository tagRepository;

    /**
     * 특정 타입의 태그를 조회한다.
     * Caffeine 캐시 적용 (TTL 5분). 3-way JOIN + GROUP BY 쿼리 비용을 줄인다
     *
     * @param tagType 태그 유형 (SECTOR, STOCK)
     * @param limit 최대 조회 수 (0이면 전체, 음수는 0으로 정규화)
     * @return 태그 목록 (클러스터 수 내림차순)
     */
    @Cacheable(value = "popularTags", key = "#tagType.name() + ':' + #limit")
    public List<PopularTag> getPopularTags(TagType tagType, int limit) {
        int safeLimit = Math.max(limit, 0);
        Pageable pageable = safeLimit == 0
                ? PageRequest.of(0, MAX_LIMIT)
                : PageRequest.of(0, Math.min(safeLimit, MAX_LIMIT));

        List<PopularTagProjection> projections = tagRepository.findPopularTags(
                tagType, ClusterStatus.ACTIVE, VISIBLE_STATUSES, pageable);

        return projections.stream()
                .map(p -> new PopularTag(p.getTagCode(), p.getTagName(), p.getClusterCount()))
                .toList();
    }

    public record PopularTag(String code, String name, long clusterCount) {
    }
}
