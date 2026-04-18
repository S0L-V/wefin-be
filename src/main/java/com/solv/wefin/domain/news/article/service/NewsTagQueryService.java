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
 * ACTIVE + 요약 완료 클러스터에 속한 기사들의 인기 태그를 조회한다.
 * limit이 0이거나 MAX_LIMIT(500)을 초과하면 최대 MAX_LIMIT 개까지 반환한다
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
     * Caffeine 캐시 적용 (TTL 5분). 3-way JOIN + GROUP BY 쿼리 비용을 줄인다.
     * 캐시 키는 정규화된 effective limit 기준이라 동일 결과는 한 엔트리만 사용한다
     *
     * @param tagType 태그 유형 (SECTOR, STOCK)
     * @param limit 최대 조회 수 (0 또는 음수, MAX_LIMIT 초과는 모두 MAX_LIMIT(500)으로 정규화)
     * @return 태그 목록 (클러스터 수 내림차순)
     */
    @Cacheable(value = "popularTags",
            key = "#tagType.name() + ':' + T(com.solv.wefin.domain.news.article.service.NewsTagQueryService).effectiveLimit(#limit)")
    public List<PopularTag> getPopularTags(TagType tagType, int limit) {
        int safeLimit = effectiveLimit(limit);
        Pageable pageable = PageRequest.of(0, safeLimit);

        List<PopularTagProjection> projections = tagRepository.findPopularTags(
                tagType, ClusterStatus.ACTIVE, VISIBLE_STATUSES, pageable);

        return projections.stream()
                .map(p -> new PopularTag(p.getTagCode(), p.getTagName(), p.getClusterCount()))
                .toList();
    }

    /**
     * 요청 limit을 실제 적용 가능한 값으로 정규화한다.
     * 0 이하 → MAX_LIMIT, MAX_LIMIT 초과 → MAX_LIMIT
     */
    public static int effectiveLimit(int requested) {
        if (requested <= 0) {
            return MAX_LIMIT;
        }
        return Math.min(requested, MAX_LIMIT);
    }

    public record PopularTag(String code, String name, long clusterCount) {
    }
}
