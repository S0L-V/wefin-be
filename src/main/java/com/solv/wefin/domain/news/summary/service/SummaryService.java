package com.solv.wefin.domain.news.summary.service;

import com.solv.wefin.domain.news.article.entity.NewsArticle;
import com.solv.wefin.domain.news.article.repository.NewsArticleRepository;
import com.solv.wefin.domain.news.cluster.entity.NewsCluster;
import com.solv.wefin.domain.news.cluster.entity.NewsCluster.ClusterStatus;
import com.solv.wefin.domain.news.cluster.entity.NewsCluster.SummaryStatus;
import com.solv.wefin.domain.news.cluster.entity.NewsClusterArticle;
import com.solv.wefin.domain.news.cluster.repository.NewsClusterArticleRepository;
import com.solv.wefin.domain.news.cluster.repository.NewsClusterRepository;
import com.solv.wefin.domain.news.summary.client.OpenAiSummaryClient;
import com.solv.wefin.domain.news.summary.dto.SummaryResult;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * AI 요약 생성 전체 흐름을 관리하는 서비스
 *
 * 한 클러스터에 대한 요약 파이프라인:
 * 이상치 제거 → 소속 기사 조회 → (단독/다건 분기) → AI 호출 → 저장
 *
 * 처리 대상 선정 기준:
 * ACTIVE 클러스터 + summaryStatus ∈ {PENDING, STALE, FAILED}.
 * STALE은 기사가 새로 추가되어 요약 재생성이 필요한 상태, FAILED는 재시도 대상이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SummaryService {

    private static final int BATCH_SIZE = 50;

    private final NewsClusterRepository newsClusterRepository;
    private final NewsClusterArticleRepository clusterArticleRepository;
    private final NewsArticleRepository newsArticleRepository;
    private final OpenAiSummaryClient openAiSummaryClient;
    private final OutlierDetectionService outlierDetectionService;
    private final SummaryPersistenceService persistenceService;

    /**
     * 요약 생성이 필요한 클러스터를 조회하여 AI 요약을 생성한다.
     *
     * 주의: 이 메서드에 @Transactional을 붙이면 안 된다.
     * AI 외부 API 호출이 트랜잭션 안에서 실행되어 DB 커넥션이 장시간 점유된다.
     * 저장은 persistenceService의 개별 @Transactional 메서드가 처리한다
     */
    public void generatePendingSummaries() {
        // 대상 조회: ACTIVE 상태 + 요약이 필요한 상태(PENDING/STALE/FAILED).
        List<NewsCluster> targets = newsClusterRepository.findByStatusAndSummaryStatusIn(
                ClusterStatus.ACTIVE,
                List.of(SummaryStatus.PENDING, SummaryStatus.STALE, SummaryStatus.FAILED),
                PageRequest.of(0, BATCH_SIZE, Sort.by(Sort.Direction.ASC, "id")));

        log.info("요약 생성 대상 클러스터 수: {}", targets.size());

        if (targets.isEmpty()) {
            return;
        }

        int successCount = 0;
        int failCount = 0;
        int outlierCount = 0;

        for (NewsCluster cluster : targets) {
            try {
                // 1) 이상치 제거 — 다건 클러스터만 대상
                if (cluster.getArticleCount() > 1) {
                    int removed = outlierDetectionService.removeOutliers(cluster);
                    outlierCount += removed;
                }

                // 2) 소속 기사 ID 조회 (한 번만 조회하여 순서 일관성 보장)
                List<Long> articleIds = getArticleIds(cluster.getId());

                if (articleIds.isEmpty()) {
                    log.warn("요약 생성 실패 — 소속 기사 없음, clusterId: {}", cluster.getId());
                    persistenceService.markFailed(cluster.getId());
                    failCount++;
                    continue;
                }

                // 3) 단독 클러스터 — AI로 본문 요약 (실패 시 fallback)
                if (articleIds.size() == 1) {
                    if (handleSingleArticleCluster(cluster, articleIds)) {
                        successCount++;
                    } else {
                        log.warn("단독 클러스터 요약 실패 — 기사 조회 불가, clusterId: {}", cluster.getId());
                        persistenceService.markFailed(cluster.getId());
                        failCount++;
                    }
                    continue;
                }

                // 4) articleIds 순서 기준으로 텍스트 생성 (프롬프트 인덱스와 ID 매핑 일치 보장)
                List<String> articleTexts = buildArticleTexts(articleIds, cluster.getId());

                // 5) AI 요약 생성 (다건 종합)
                SummaryResult result = openAiSummaryClient.generateSummary(articleTexts);

                if (result.isEmpty()) {
                    throw new BusinessException(ErrorCode.SUMMARY_EMPTY_RESULT);
                }

                // 6) 섹션 검증 — 실제 articleIds 기준으로 유효한 출처가 있는 섹션이 1개 이상 필요
                if (!result.hasSections()) {
                    log.warn("섹션 배열이 비어있음 — clusterId: {}", cluster.getId());
                    throw new BusinessException(ErrorCode.SUMMARY_NO_SECTIONS);
                }

                int articleCount = articleIds.size();
                boolean hasValidSection = result.getSections().stream()
                        .anyMatch(s -> s.isValid() && hasValidSourceIndex(s, articleCount));
                if (!hasValidSection) {
                    log.warn("유효한 출처를 가진 섹션 없음 — clusterId: {}", cluster.getId());
                    throw new BusinessException(ErrorCode.SUMMARY_NO_VALID_SECTIONS);
                }

                // 7) 저장 — 기사 집합 변경 감지 후 섹션/출처 저장, 마지막에 GENERATED 마킹
                persistenceService.markGeneratedWithSections(cluster.getId(), result.getTitle(),
                        result.getLeadSummary(), result.getSections(), articleIds);
                successCount++;

            } catch (StaleClusterException e) {
                // 기사 집합 불일치: 클러스터가 변경되어 저장을 건너뜀. FAILED로 마킹하지 않는다
                log.info("요약 저장 스킵 — clusterId: {}, reason: {}", cluster.getId(), e.getMessage());
            } catch (Exception e) {
                log.warn("요약 생성 실패 — clusterId: {}, error: {}", cluster.getId(), e.getMessage());
                try {
                    persistenceService.markFailed(cluster.getId());
                } catch (Exception ex) {
                    log.error("요약 실패 마킹도 실패 — clusterId: {}", cluster.getId());
                }
                failCount++;
            }
        }

        log.info("요약 생성 완료 — 성공: {}, 실패: {}, 이상치 제거: {}건", successCount, failCount, outlierCount);
    }

    /**
     * 단독 클러스터(기사 1건)는 AI로 본문을 요약한다.
     *
     * 크롤링된 원문에는 광고, 기자 서명 등 노이즈가 포함되어 있으므로
     * AI가 핵심 내용만 추출하여 title + summary를 생성한다.
     * AI 호출 실패 시 기존 fallback(제목 클렌징 + 기사 요약 그대로 사용)으로 처리한다
     *
     * @param cluster 요약 대상 클러스터
     * @param expectedArticleIds 조회 시점의 기사 ID 목록 (기사 집합 변경 감지용)
     * @return true면 성공, false면 기사를 찾지 못해 실패
     * @throws StaleClusterException 저장 직전 기사 집합이 변경된 경우
     */
    private boolean handleSingleArticleCluster(NewsCluster cluster, List<Long> expectedArticleIds) {
        Long articleId = expectedArticleIds.get(0);
        var articleOpt = newsArticleRepository.findById(articleId);
        if (articleOpt.isEmpty()) {
            return false;
        }

        var article = articleOpt.get();
        String title;
        String summary;

        // 본문이 있으면 AI 요약, 없으면 기존 fallback
        if (article.getContent() != null && !article.getContent().isBlank()) {
            try {
                SummaryResult result = openAiSummaryClient.generateSingleArticleSummary(
                        article.getTitle(), article.getContent());
                title = result.getTitle() != null && !result.getTitle().isBlank()
                        ? result.getTitle() : resolveTitle(article);
                summary = result.getLeadSummary() != null && !result.getLeadSummary().isBlank()
                        ? result.getLeadSummary() : resolveSummaryFallback(article, title);
                log.info("단독 클러스터 AI 요약 성공 — clusterId: {}, articleId: {}",
                        cluster.getId(), articleId);
            } catch (Exception e) {
                log.warn("단독 클러스터 AI 요약 실패, fallback 사용 — clusterId: {}, error: {}",
                        cluster.getId(), e.getMessage());
                title = resolveTitle(article);
                summary = resolveSummaryFallback(article, title);
            }
        } else {
            title = resolveTitle(article);
            summary = resolveSummaryFallback(article, title);
        }

        persistenceService.markGeneratedSingle(cluster.getId(), title, summary, expectedArticleIds);
        return true;
    }

    /**
     * 기사의 summary를 반환한다. null이거나 blank이면 title을 fallback으로 사용한다
     */
    private String resolveSummaryFallback(com.solv.wefin.domain.news.article.entity.NewsArticle article, String title) {
        String summary = article.getSummary();
        return summary != null && !summary.isBlank() ? summary : title;
    }

    /**
     * 단독 클러스터 title을 3단계 fallback으로 결정한다.
     * 1단계: 규칙 기반 클렌징 → 2단계: AI 재생성 → 3단계: 원본 그대로
     */
    private String resolveTitle(com.solv.wefin.domain.news.article.entity.NewsArticle article) {
        String original = article.getTitle();

        // 1단계: 규칙 기반 클렌징
        String cleansed = TitleCleanser.cleanse(original);

        if (!TitleCleanser.needsAiFallback(cleansed, original)) {
            if (!cleansed.equals(original)) {
                log.debug("단독 title 클렌징 — articleId: {}, before: {}, after: {}", article.getId(), original, cleansed);
            }
            return cleansed;
        }

        // 2단계: AI fallback (클렌징 결과가 너무 짧음)
        log.info("단독 title AI fallback — articleId: {}, cleansed: '{}' ({}자)", article.getId(), cleansed, cleansed.length());
        try {
            String rawAiTitle = openAiSummaryClient.generateSingleTitle(original, article.getContent());
            String aiTitle = TitleCleanser.sanitizeAiTitle(rawAiTitle);
            if (aiTitle != null) {
                log.info("단독 title AI 재생성 성공 — articleId: {}, title: {}", article.getId(), aiTitle);
                return aiTitle;
            }
        } catch (Exception e) {
            log.warn("단독 title AI 재생성 실패 — articleId: {}", article.getId(), e);
        }

        // 3단계: 원본 그대로
        return original;
    }

    /**
     * 클러스터에 속한 기사 ID 목록을 조회한다
     */
    private List<Long> getArticleIds(Long clusterId) {
        return clusterArticleRepository.findByNewsClusterId(clusterId).stream()
                .map(NewsClusterArticle::getNewsArticleId)
                .toList();
    }

    /**
     * 섹션의 sourceArticleIndices 중 실제 articleIds 범위 내 유효한 인덱스가 있는지 확인한다
     */
    private boolean hasValidSourceIndex(SummaryResult.SectionItem section, int articleCount) {
        if (!section.hasSources()) {
            return false;
        }
        return section.getSourceArticleIndices().stream()
                .anyMatch(idx -> idx >= 1 && idx <= articleCount);
    }

    /**
     * articleIds 순서를 유지하면서 "제목 + 본문" 텍스트 목록을 생성한다.
     * 누락된 기사가 있으면 인덱스 매핑이 어긋나므로 즉시 예외를 던진다
     */
    private List<String> buildArticleTexts(List<Long> articleIds, Long clusterId) {
        Map<Long, NewsArticle> articleMap = newsArticleRepository.findAllById(articleIds).stream()
                .collect(Collectors.toMap(NewsArticle::getId, a -> a));

        if (articleMap.size() != articleIds.size()) {
            log.error("기사 조회 불일치 — clusterId: {}, expected: {}, actual: {}",
                    clusterId, articleIds.size(), articleMap.size());
            throw new BusinessException(ErrorCode.SUMMARY_ARTICLE_MISMATCH);
        }

        return articleIds.stream()
                .map(id -> {
                    NewsArticle a = articleMap.get(id);
                    return "제목: " + a.getTitle() + "\n본문: " +
                            (a.getContent() != null ? a.getContent() : "");
                })
                .toList();
    }
}
