package com.solv.wefin.domain.news.summary.service;

import com.solv.wefin.domain.news.article.repository.NewsArticleRepository;
import com.solv.wefin.domain.news.cluster.entity.NewsCluster;
import com.solv.wefin.domain.news.cluster.entity.NewsCluster.ClusterStatus;
import com.solv.wefin.domain.news.cluster.entity.NewsCluster.SummaryStatus;
import com.solv.wefin.domain.news.cluster.entity.NewsClusterArticle;
import com.solv.wefin.domain.news.cluster.repository.NewsClusterArticleRepository;
import com.solv.wefin.domain.news.cluster.repository.NewsClusterRepository;
import com.solv.wefin.domain.news.summary.client.OpenAiSummaryClient;
import com.solv.wefin.domain.news.summary.dto.SummaryResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;

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

                // 2) 소속 기사 조회
                List<String> articleTexts = getArticleTexts(cluster.getId());

                if (articleTexts.isEmpty()) {
                    log.warn("요약 생성 실패 — 소속 기사 없음, clusterId: {}", cluster.getId());
                    persistenceService.markFailed(cluster.getId());
                    failCount++;
                    continue;
                }

                // 3) 단독 클러스터 최적화 — AI 호출 없이 기사 제목/요약을 그대로 사용.
                if (articleTexts.size() == 1) {
                    if (handleSingleArticleCluster(cluster)) {
                        successCount++;
                    } else {
                        log.warn("단독 클러스터 요약 실패 — 기사 조회 불가, clusterId: {}", cluster.getId());
                        persistenceService.markFailed(cluster.getId());
                        failCount++;
                    }
                    continue;
                }

                // 4) AI 요약 생성 (다건 종합)
                SummaryResult result = openAiSummaryClient.generateSummary(articleTexts);

                if (result.isEmpty()) {
                    throw new IllegalStateException("AI 요약 결과가 비어있습니다");
                }

                // 5) 저장 — GENERATED 상태로 마킹하며 title/summary를 커밋
                persistenceService.markGenerated(cluster.getId(), result.getTitle(), result.getSummary());
                successCount++;

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
     * 단독 클러스터(기사 1건)는 AI 호출 없이 기사 제목/요약을 그대로 사용한다.
     *
     * @return true면 성공, false면 기사를 찾지 못해 실패
     */
    private boolean handleSingleArticleCluster(NewsCluster cluster) {
        // 매핑 재조회 (이상치 제거 후의 실제 상태를 반영)
        List<NewsClusterArticle> mappings = clusterArticleRepository.findByNewsClusterId(cluster.getId());
        // 단독 클러스터인데 매핑이 사라진 경우 (동시성/데이터 이상)
        if (mappings.isEmpty()) {
            return false;
        }

        Long articleId = mappings.get(0).getNewsArticleId();
        var articleOpt = newsArticleRepository.findById(articleId);
        // 매핑은 있는데 기사가 없는 경우 (기사 삭제 후 매핑 미정리)
        if (articleOpt.isEmpty()) {
            return false;
        }

        var article = articleOpt.get();
        String title = resolveTitle(article);
        String summary = article.getSummary() != null ? article.getSummary() : title;
        persistenceService.markGenerated(cluster.getId(), title, summary);
        return true;
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
     * 클러스터에 속한 기사들을 "제목 + 본문" 문자열 형태로 변환한다.
     */
    private List<String> getArticleTexts(Long clusterId) {
        // 1. 클러스터-기사 매핑 조회 (현재 소속된 기사만)
        List<NewsClusterArticle> mappings = clusterArticleRepository.findByNewsClusterId(clusterId);

        // 2. 기사 ID 추출
        List<Long> articleIds = mappings.stream()
                .map(NewsClusterArticle::getNewsArticleId)
                .toList();

        // 3. 기사 일괄 조회 + "제목 + 본문" 형태로 변환
        return newsArticleRepository.findAllById(articleIds).stream()
                .map(article -> "제목: " + article.getTitle() + "\n본문: " +
                        (article.getContent() != null ? article.getContent() : ""))
                .toList();
    }
}
