package com.solv.wefin.domain.news.cluster.service;

import com.solv.wefin.domain.news.article.entity.NewsArticleTag;
import com.solv.wefin.domain.news.article.entity.NewsArticleTag.TagType;
import com.solv.wefin.domain.news.article.repository.NewsArticleTagRepository;
import com.solv.wefin.domain.news.cluster.entity.NewsClusterArticle;
import com.solv.wefin.domain.news.cluster.repository.NewsClusterArticleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 태그 기반 suspicious scoring 서비스
 *
 * 임베딩 유사도 매칭을 통과한 기사에 대해 태그 기반 추가 검증을 수행한다.
 * 감점 방식(scoring)으로 의심 신호를 누적하며,
 * 최종적으로 NORMAL / SUSPICIOUS / REJECT 상태를 판정한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SuspiciousScoringService {

    private final NewsArticleTagRepository articleTagRepository;
    private final NewsClusterArticleRepository clusterArticleRepository;

    private static final int BASE_SCORE = 100; // 기본 점수

    @Value("${clustering.penalty.category-mismatch:15}")
    private int categoryMismatchPenalty; // SECTOR/TOPIC 교집합 없을 때 감점

    @Value("${clustering.penalty.stocks-no-overlap:20}")
    private int stocksNoOverlapPenalty; // STOCK 교집합 없을 때 감점

    @Value("${clustering.score.reject-cutoff:60}")
    private int rejectCutoff; // 특정 점수 미만 → 클러스터 매칭 거부

    @Value("${clustering.score.suspicious-cutoff:80}")
    private int suspiciousCutoff; // 특정 점수 미만 → suspicious 플래그

    /**
     * 새 기사와 클러스터 간 태그 기반 점수를 계산한다.
     *
     * @param articleId 새 기사 ID
     * @param clusterId 대상 클러스터 ID
     * @return scoring 결과 (점수 + 판정)
     */
    public ScoreResult score(Long articleId, Long clusterId) {
        // 1. 새 기사 태그 조회
        List<NewsArticleTag> articleTags = articleTagRepository.findByNewsArticleId(articleId);

        // 태깅이 아직 안 된 경우 → 검증 불가 → 통과 처리
        if (articleTags.isEmpty()) {
            return new ScoreResult(BASE_SCORE, Verdict.NORMAL);
        }

        // 2. 클러스터에 속한 기사들 조회
        List<NewsClusterArticle> mappings = clusterArticleRepository.findByNewsClusterId(clusterId);

        // 클러스터가 비어있으면 비교 대상 없음 → 통과 처리
        if (mappings.isEmpty()) {
            return new ScoreResult(BASE_SCORE, Verdict.NORMAL);
        }

        // 클러스터에 포함된 기사 ID 목록 추출
        List<Long> clusterArticleIds = mappings.stream()
                .map(NewsClusterArticle::getNewsArticleId)
                .toList();

        // 3. 클러스터 전체 태그 조회
        List<NewsArticleTag> clusterTags = articleTagRepository.findByNewsArticleIdIn(clusterArticleIds);

        // 클러스터 기준 태그 분리
        Set<String> clusterStockCodes = filterTagCodes(clusterTags, TagType.STOCK);
        Set<String> clusterSectorCodes = filterTagCodes(clusterTags, TagType.SECTOR);
        Set<String> clusterTopicCodes = filterTagCodes(clusterTags, TagType.TOPIC);

        // 기사 기준 태그 분리
        Set<String> articleStockCodes = filterTagCodes(articleTags, TagType.STOCK);

        // 기사 category (SECTOR + TOPIC)
        Set<String> articleCategoryCodes = articleTags.stream()
                .filter(t -> t.getTagType() == TagType.SECTOR || t.getTagType() == TagType.TOPIC)
                .map(NewsArticleTag::getTagCode)
                .collect(Collectors.toSet());

        // 4. 감점 기반 scoring 시작
        int score = BASE_SCORE;

        // 4-1. category 교집합 검사 (SECTOR + TOPIC)
        Set<String> clusterCategoryCodes = new HashSet<>(clusterSectorCodes);
        clusterCategoryCodes.addAll(clusterTopicCodes);

        boolean categoryOverlap = articleCategoryCodes.stream()
                .anyMatch(clusterCategoryCodes::contains);

        // 둘 다 값이 있는데 교집합이 없으면 감점
        if (!categoryOverlap && !articleCategoryCodes.isEmpty() && !clusterCategoryCodes.isEmpty()) {
            score -= categoryMismatchPenalty;
        }

        // 4-2. STOCK 교집합 검사
        boolean stockOverlap = articleStockCodes.stream()
                .anyMatch(clusterStockCodes::contains);

        // 둘 다 값이 있는데 교집합이 없으면 감점
        if (!stockOverlap && !articleStockCodes.isEmpty() && !clusterStockCodes.isEmpty()) {
            score -= stocksNoOverlapPenalty;
        }

        // 5. 점수 기반 최종 판정
        Verdict verdict;
        if (score < rejectCutoff) {
            verdict = Verdict.REJECT;
        } else if (score < suspiciousCutoff) {
            verdict = Verdict.SUSPICIOUS;
        } else {
            verdict = Verdict.NORMAL;
        }

        return new ScoreResult(score, verdict);
    }

    /**
     * 특정 타입(STOCK / SECTOR / TOPIC)의 태그 코드만 추출한다.
     */
    private Set<String> filterTagCodes(List<NewsArticleTag> tags, TagType tagType) {
        return tags.stream()
                .filter(t -> t.getTagType() == tagType)
                .map(NewsArticleTag::getTagCode)
                .collect(Collectors.toSet());
    }

    /**
     * 점수 판정 결과
     *
     * NORMAL     : 정상 클러스터 매칭
     * SUSPICIOUS : 의심 (품질 검증/모니터링 대상)
     * REJECT     : 클러스터 매칭 거부 (새 클러스터 생성 필요)
     */
    public enum Verdict {
        NORMAL, SUSPICIOUS, REJECT
    }

    public record ScoreResult(int score, Verdict verdict) {
    }
}
