package com.solv.wefin.domain.news.summary.service;

import com.solv.wefin.domain.news.cluster.entity.ClusterSuggestedQuestion;
import com.solv.wefin.domain.news.cluster.entity.ClusterSummarySection;
import com.solv.wefin.domain.news.cluster.entity.ClusterSummarySectionSource;
import com.solv.wefin.domain.news.cluster.entity.NewsCluster;
import com.solv.wefin.domain.news.cluster.entity.NewsClusterArticle;
import com.solv.wefin.domain.news.cluster.repository.ClusterSuggestedQuestionRepository;
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

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
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

    private static final int REQUIRED_QUESTION_COUNT = 3;
    private static final int MAX_QUESTION_LENGTH = 200; // 추천 질문 1건의 최대 길이 (XSS/레이아웃 오염 방지)

    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

    private final NewsClusterRepository newsClusterRepository;
    private final NewsClusterArticleRepository clusterArticleRepository;
    private final ClusterSummarySectionRepository sectionRepository;
    private final ClusterSummarySectionSourceRepository sectionSourceRepository;
    private final ClusterSuggestedQuestionRepository questionRepository;

    /**
     * 단독 클러스터의 AI 요약 생성 성공을 반영한다 (섹션 없음)
     * 저장 직전 기사 집합이 변경되었으면 StaleClusterException을 던진다.
     * 추천 질문이 정규화 후 정확히 3개일 때만 기존 질문을 교체한다 (그 외는 기존 유지)
     *
     * @param clusterId 클러스터 ID
     * @param title 대표 제목
     * @param summary Lead 요약
     * @param questions 추천 질문 목록 (nullable)
     * @param expectedArticleIds 조회 시점의 기사 ID 목록 (기사 집합 변경 감지용)
     * @throws StaleClusterException 기사 집합이 변경된 경우
     */
    @Transactional
    public void markGeneratedSingle(Long clusterId, String title, String summary,
                                    List<String> questions, List<Long> expectedArticleIds) {
        // 동시 실행 방어: 같은 클러스터 row에 쓰기 락을 걸어 직렬화한다
        findClusterForUpdate(clusterId);
        verifyArticlesUnchanged(clusterId, expectedArticleIds);

        // 다건→단독 전환 시 기존 섹션/출처/질문을 먼저 모두 삭제한다 (단독은 섹션 없음)
        sectionRepository.deleteSourcesByNewsClusterId(clusterId);
        sectionRepository.deleteByNewsClusterId(clusterId);
        questionRepository.deleteByNewsClusterId(clusterId);

        // 정규화 후 3개일 때만 새 질문 저장 (미달 시 기존은 이미 삭제되어 비어있음)
        saveNormalizedQuestions(clusterId, questions);

        // bulk delete의 clearAutomatically로 영속성 컨텍스트가 초기화되므로 재조회한다
        NewsCluster cluster = findCluster(clusterId);
        cluster.markSummaryGenerated(title, summary);
    }

    /**
     * 다건 클러스터의 AI 요약 생성 성공을 반영한다 (섹션 포함)
     *
     * 저장 순서: 기사 집합 변경 감지 → 기존 섹션 삭제 → 새 섹션/출처 저장 → 마지막에 GENERATED 마킹.
     * 저장 직전 기사 집합이 변경되었으면 StaleClusterException을 던진다.
     * 유효한 출처가 없는 섹션은 저장하지 않는다(드롭).
     * 모든 섹션이 드롭되면 예외를 던진다
     *
     * @param clusterId 클러스터 ID
     * @param title 대표 제목
     * @param leadSummary Lead 요약
     * @param sections AI가 생성한 섹션 목록 (nullable)
     * @param articleIds 프롬프트에 전달된 기사 ID 목록 (인덱스 매핑 + 기사 집합 변경 감지용)
     * @throws StaleClusterException 기사 집합이 변경된 경우
     */
    @Transactional
    public void markGeneratedWithSections(Long clusterId, String title, String leadSummary,
                                          List<SummaryResult.SectionItem> sections,
                                          List<String> questions, List<Long> articleIds) {
        // 동시 실행 방어: 같은 클러스터 row에 쓰기 락을 걸어 직렬화한다
        findClusterForUpdate(clusterId);
        verifyArticlesUnchanged(clusterId, articleIds);

        // 1) 기존 섹션/질문 삭제 (STALE 재생성 대응)
        sectionRepository.deleteSourcesByNewsClusterId(clusterId);
        sectionRepository.deleteByNewsClusterId(clusterId);
        questionRepository.deleteByNewsClusterId(clusterId);

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

        // 4) 새 질문 저장 (기존은 1단계에서 이미 삭제됨). 정규화 후 3개 미만이면 저장 스킵
        saveNormalizedQuestions(clusterId, questions);

        // 5) bulk delete의 clearAutomatically로 영속성 컨텍스트가 초기화되었으므로 재조회
        NewsCluster cluster = findCluster(clusterId);
        cluster.markSummaryGenerated(title, leadSummary);
    }

    /**
     * 정규화된 추천 질문을 저장한다
     *
     * 기존 삭제 후 정규화 결과가 {@link #REQUIRED_QUESTION_COUNT}개 이상이면 앞 3개만 저장, 미달이면 저장 스킵
     *
     * @param clusterId 클러스터 ID
     * @param questions AI가 생성한 추천 질문 목록 (nullable)
     */
    private void saveNormalizedQuestions(Long clusterId, List<String> questions) {
        List<String> normalized = normalizeQuestions(questions);

        if (normalized.size() < REQUIRED_QUESTION_COUNT) {
            log.warn("추천 질문 저장 스킵 — clusterId: {}, normalized count: {}, 질문 없음 확정",
                    clusterId, normalized.size());
            return;
        }

        if (questions != null && questions.size() > REQUIRED_QUESTION_COUNT) {
            log.warn("AI 응답이 {}개를 초과함 — clusterId: {}, raw count: {}, 앞 {}개만 저장",
                    REQUIRED_QUESTION_COUNT, clusterId, questions.size(), REQUIRED_QUESTION_COUNT);
        }

        for (int i = 0; i < REQUIRED_QUESTION_COUNT; i++) {
            questionRepository.save(ClusterSuggestedQuestion.create(clusterId, i, normalized.get(i)));
        }

        log.info("추천 질문 저장 완료 — clusterId: {}, count: {}", clusterId, REQUIRED_QUESTION_COUNT);
    }

    /**
     * 추천 질문 목록을 정규화한다
     *
     * 처리 순서:
     * 1. null 원소 제거
     * 2. 제어 문자(개행/탭/0x00~0x1F 등) 공백으로 치환
     * 3. 연속 공백 1개로 축소 + trim
     * 4. blank 제거
     * 5. 최대 길이({@link #MAX_QUESTION_LENGTH}자) 초과 시 컷
     * 6. 중복 제거 (정규화된 키 기준, insertion order 유지)
     */
    static List<String> normalizeQuestions(List<String> questions) {
        if (questions == null || questions.isEmpty()) {
            return List.of();
        }
        Set<String> seen = new LinkedHashSet<>();
        for (String q : questions) {
            String normalized = normalizeOne(q);
            if (normalized == null) {
                continue;
            }
            seen.add(normalized);
        }
        return List.copyOf(seen);
    }

    private static String normalizeOne(String q) {
        if (q == null) {
            return null;
        }
        // 제어 문자를 공백으로 치환 (탭, 개행, 0x00~0x1F)
        String cleaned = q.replaceAll("[\\p{Cntrl}]", " ");
        // 연속 공백 1개로 축소
        cleaned = WHITESPACE_PATTERN.matcher(cleaned).replaceAll(" ").trim();
        if (cleaned.isEmpty()) {
            return null;
        }
        if (cleaned.length() > MAX_QUESTION_LENGTH) {
            cleaned = cleaned.substring(0, MAX_QUESTION_LENGTH);
        }
        return cleaned;
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
     * 클러스터를 쓰기 락으로 조회한다 (동시 실행 시 직렬화 보장)
     */
    private NewsCluster findClusterForUpdate(Long clusterId) {
        return newsClusterRepository.findByIdForUpdate(clusterId)
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
