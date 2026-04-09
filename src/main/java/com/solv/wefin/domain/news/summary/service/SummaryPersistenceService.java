package com.solv.wefin.domain.news.summary.service;

import com.solv.wefin.domain.news.cluster.entity.ClusterSummarySection;
import com.solv.wefin.domain.news.cluster.entity.ClusterSummarySectionSource;
import com.solv.wefin.domain.news.cluster.entity.NewsCluster;
import com.solv.wefin.domain.news.cluster.entity.NewsClusterArticle;
import com.solv.wefin.domain.news.cluster.repository.ClusterSummarySectionRepository;
import com.solv.wefin.domain.news.cluster.repository.ClusterSummarySectionSourceRepository;
import com.solv.wefin.domain.news.cluster.repository.NewsClusterArticleRepository;
import com.solv.wefin.domain.news.cluster.repository.NewsClusterRepository;
import com.solv.wefin.domain.news.summary.dto.SummaryResult;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * AI 요약 결과를 DB에 반영하는 서비스
 *
 * 클러스터 요약(title, summary)과 상세 섹션(heading, body, 출처 매핑)을 저장한다.
 * STALE 재생성 시 기존 섹션을 삭제한 뒤 새로 생성한다.
 * 저장 직전 기사 집합 변경을 감지하여 낡은 요약이 저장되는 것을 방지한다
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SummaryPersistenceService {

    private final NewsClusterRepository newsClusterRepository;
    private final NewsClusterArticleRepository clusterArticleRepository;
    private final ClusterSummarySectionRepository sectionRepository;
    private final ClusterSummarySectionSourceRepository sectionSourceRepository;

    /**
     * 단독 클러스터의 AI 요약 생성 성공을 반영한다 (섹션 없음)
     * 저장 직전 기사 집합이 변경되었으면 StaleClusterException을 던진다
     *
     * @param clusterId 클러스터 ID
     * @param title 대표 제목
     * @param summary Lead 요약
     * @param expectedArticleIds 조회 시점의 기사 ID 목록 (CAS 검증용)
     * @throws StaleClusterException 기사 집합이 변경된 경우
     */
    @Transactional
    public void markGeneratedSingle(Long clusterId, String title, String summary,
                                    List<Long> expectedArticleIds) {
        findCluster(clusterId);
        verifyArticlesUnchanged(clusterId, expectedArticleIds);

        // 다건→단독 전환 시 기존 섹션/출처를 정리한다
        sectionRepository.deleteSourcesByNewsClusterId(clusterId);
        sectionRepository.deleteByNewsClusterId(clusterId);

        // bulk delete의 clearAutomatically로 영속성 컨텍스트가 초기화되므로 재조회한다
        NewsCluster cluster = findCluster(clusterId);
        cluster.markSummaryGenerated(title, summary);
    }

    /**
     * 다건 클러스터의 AI 요약 생성 성공을 반영한다 (섹션 포함)
     *
     * 저장 순서: CAS(Compare-And-Swap, 비교 후 교체) 검증 → 기존 섹션 삭제 → 새 섹션/출처 저장 → 마지막에 GENERATED 마킹.
     * 저장 직전 기사 집합이 변경되었으면 StaleClusterException을 던진다.
     * 유효한 출처가 없는 섹션은 저장하지 않는다(드롭).
     * 모든 섹션이 드롭되면 예외를 던진다
     *
     * @param clusterId 클러스터 ID
     * @param title 대표 제목
     * @param leadSummary Lead 요약
     * @param sections AI가 생성한 섹션 목록 (nullable)
     * @param articleIds 프롬프트에 전달된 기사 ID 목록 (인덱스 매핑 + CAS 검증용)
     * @throws StaleClusterException 기사 집합이 변경된 경우
     */
    @Transactional
    public void markGeneratedWithSections(Long clusterId, String title, String leadSummary,
                                          List<SummaryResult.SectionItem> sections, List<Long> articleIds) {
        findCluster(clusterId);
        verifyArticlesUnchanged(clusterId, articleIds);

        // 1) 기존 섹션 삭제 (STALE 재생성 대응)
        sectionRepository.deleteSourcesByNewsClusterId(clusterId);
        sectionRepository.deleteByNewsClusterId(clusterId);

        // 2) 새 섹션/출처 저장 — 유효한 출처가 없는 섹션은 드롭
        int savedOrder = 0;
        if (sections != null) {
            for (int i = 0; i < sections.size(); i++) {
                SummaryResult.SectionItem item = sections.get(i);

                if (!item.isValid()) {
                    log.warn("섹션 스킵 — clusterId: {}, index: {}, 유효하지 않은 섹션", clusterId, i);
                    continue;
                }

                List<Long> validArticleIds = resolveValidArticleIds(item, articleIds);
                if (validArticleIds.isEmpty()) {
                    log.warn("섹션 드롭 — clusterId: {}, index: {}, 유효한 출처 없음", clusterId, i);
                    continue;
                }

                ClusterSummarySection section = ClusterSummarySection.create(
                        clusterId, savedOrder++, item.getHeading(), item.getBody());
                sectionRepository.save(section);

                for (Long articleId : validArticleIds) {
                    sectionSourceRepository.save(ClusterSummarySectionSource.create(section.getId(), articleId));
                }
            }
        }

        // 3) 유효 섹션이 하나도 저장되지 않으면 GENERATED로 마킹하지 않는다
        if (savedOrder == 0) {
            throw new BusinessException(ErrorCode.SUMMARY_NO_VALID_SECTIONS);
        }

        // 4) bulk delete의 clearAutomatically로 영속성 컨텍스트가 초기화되었으므로 재조회한다
        NewsCluster cluster = findCluster(clusterId);
        cluster.markSummaryGenerated(title, leadSummary);
    }

    /**
     * AI 요약 생성 실패를 반영한다
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long clusterId) {
        NewsCluster cluster = findCluster(clusterId);
        cluster.markSummaryFailed();
    }

    /**
     * AI 호출 시점의 기사 집합과 저장 시점의 기사 집합이 동일한지 검증한다
     *
     * AI 호출은 수 초가 걸리므로 그 사이에 클러스터링 배치가 기사를 추가/제거/교체할 수 있다.
     * 변경된 상태에서 그대로 저장하면 출처가 현재 클러스터와 맞지 않는 요약이 노출된다.
     * Set 비교로 개수가 같아도 멤버가 다르면 감지한다 (예: [A,B] → [A,C]).
     * 불일치 시 FAILED가 아닌 skip 처리되어 다음 배치에서 최신 기사 기준으로 재처리된다
     *
     * @param clusterId 클러스터 ID
     * @param expectedArticleIds AI 호출 시점에 조회한 기사 ID 목록
     * @throws StaleClusterException 기사 집합이 변경된 경우
     */
    private void verifyArticlesUnchanged(Long clusterId, List<Long> expectedArticleIds) {
        Set<Long> currentIds = clusterArticleRepository.findByNewsClusterId(clusterId).stream()
                .map(NewsClusterArticle::getNewsArticleId)
                .collect(Collectors.toSet());

        Set<Long> expectedIds = Set.copyOf(expectedArticleIds);

        if (!currentIds.equals(expectedIds)) {
            log.warn("클러스터 기사 집합 변경 감지 — clusterId: {}, expected: {}, actual: {}",
                    clusterId, expectedIds, currentIds);
            throw new StaleClusterException(
                    "클러스터 기사 집합이 변경되었습니다 — clusterId: " + clusterId);
        }
    }

    private NewsCluster findCluster(Long clusterId) {
        return newsClusterRepository.findById(clusterId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SUMMARY_CLUSTER_NOT_FOUND));
    }

    /**
     * 섹션의 sourceArticleIndices를 실제 article ID로 매핑하여 유효한 ID 목록을 반환한다.
     * 범위 밖 인덱스는 제외하고, 중복은 제거한다
     */
    private List<Long> resolveValidArticleIds(SummaryResult.SectionItem item, List<Long> articleIds) {
        if (!item.hasSources()) {
            return List.of();
        }
        return item.getSourceArticleIndices().stream()
                .distinct()
                .filter(idx -> idx >= 1 && idx <= articleIds.size())
                .map(idx -> articleIds.get(idx - 1))
                .toList();
    }
}
