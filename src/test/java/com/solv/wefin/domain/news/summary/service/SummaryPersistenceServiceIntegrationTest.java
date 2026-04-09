package com.solv.wefin.domain.news.summary.service;

import com.solv.wefin.common.IntegrationTestBase;
import com.solv.wefin.domain.news.article.entity.NewsArticle;
import com.solv.wefin.domain.news.article.repository.NewsArticleRepository;
import com.solv.wefin.domain.news.cluster.entity.ClusterSummarySection;
import com.solv.wefin.domain.news.cluster.entity.ClusterSummarySectionSource;
import com.solv.wefin.domain.news.cluster.entity.NewsCluster;
import com.solv.wefin.domain.news.cluster.entity.NewsClusterArticle;
import com.solv.wefin.domain.news.cluster.repository.ClusterSummarySectionRepository;
import com.solv.wefin.domain.news.cluster.repository.ClusterSummarySectionSourceRepository;
import com.solv.wefin.domain.news.cluster.repository.NewsClusterArticleRepository;
import com.solv.wefin.domain.news.cluster.repository.NewsClusterRepository;
import com.solv.wefin.domain.news.summary.dto.SummaryResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SummaryPersistenceServiceIntegrationTest extends IntegrationTestBase {

    @Autowired
    private SummaryPersistenceService persistenceService;

    @Autowired
    private NewsClusterRepository clusterRepository;

    @Autowired
    private NewsClusterArticleRepository clusterArticleRepository;

    @Autowired
    private ClusterSummarySectionRepository sectionRepository;

    @Autowired
    private ClusterSummarySectionSourceRepository sectionSourceRepository;

    @Autowired
    private NewsArticleRepository articleRepository;

    @Test
    @DisplayName("섹션 + 출처 매핑이 정상 저장된다")
    void markGenerated_withSections_savesSectionsAndSources() {
        // given
        NewsArticle article1 = createAndSaveArticle();
        NewsArticle article2 = createAndSaveArticle();
        NewsCluster cluster = createAndSaveCluster(article1.getId(), 2);
        List<Long> articleIds = List.of(article1.getId(), article2.getId());
        createClusterArticleMappings(cluster.getId(), articleIds);

        List<SummaryResult.SectionItem> sections = List.of(
                createSectionItem("첫 번째 소제목", "첫 번째 본문 내용", List.of(1, 2)),
                createSectionItem("두 번째 소제목", "두 번째 본문 내용", List.of(2))
        );

        // when
        persistenceService.markGeneratedWithSections(cluster.getId(), "제목", "리드 요약", sections, articleIds);

        // then
        List<ClusterSummarySection> savedSections =
                sectionRepository.findByNewsClusterIdOrderBySectionOrderAsc(cluster.getId());
        assertThat(savedSections).hasSize(2);
        assertThat(savedSections.get(0).getHeading()).isEqualTo("첫 번째 소제목");
        assertThat(savedSections.get(0).getSectionOrder()).isEqualTo(0);
        assertThat(savedSections.get(1).getHeading()).isEqualTo("두 번째 소제목");
        assertThat(savedSections.get(1).getSectionOrder()).isEqualTo(1);

        // 첫 번째 섹션: 출처 2개
        List<ClusterSummarySectionSource> sources0 =
                sectionSourceRepository.findByClusterSummarySectionId(savedSections.get(0).getId());
        assertThat(sources0).hasSize(2);

        // 두 번째 섹션: 출처 1개
        List<ClusterSummarySectionSource> sources1 =
                sectionSourceRepository.findByClusterSummarySectionId(savedSections.get(1).getId());
        assertThat(sources1).hasSize(1);
        assertThat(sources1.get(0).getNewsArticleId()).isEqualTo(article2.getId());
    }

    @Test
    @DisplayName("STALE 재생성 시 기존 섹션이 삭제되고 새 섹션으로 교체된다")
    void markGenerated_staleRegeneration_replacesExistingSections() {
        // given
        NewsArticle article1 = createAndSaveArticle();
        NewsArticle article2 = createAndSaveArticle();
        NewsCluster cluster = createAndSaveCluster(article1.getId(), 2);
        List<Long> articleIds = List.of(article1.getId(), article2.getId());
        createClusterArticleMappings(cluster.getId(), articleIds);

        // 1차 생성
        List<SummaryResult.SectionItem> firstSections = List.of(
                createSectionItem("기존 소제목 A", "기존 본문 A", List.of(1)),
                createSectionItem("기존 소제목 B", "기존 본문 B", List.of(2))
        );
        persistenceService.markGeneratedWithSections(cluster.getId(), "기존 제목", "기존 요약", firstSections, articleIds);

        List<ClusterSummarySection> afterFirst =
                sectionRepository.findByNewsClusterIdOrderBySectionOrderAsc(cluster.getId());
        assertThat(afterFirst).hasSize(2);
        Long oldSectionId = afterFirst.get(0).getId();

        // 2차 재생성 (STALE → 섹션 교체)
        List<SummaryResult.SectionItem> newSections = List.of(
                createSectionItem("새 소제목 X", "새 본문 X", List.of(1, 2)),
                createSectionItem("새 소제목 Y", "새 본문 Y", List.of(1)),
                createSectionItem("새 소제목 Z", "새 본문 Z", List.of(2))
        );

        // when
        persistenceService.markGeneratedWithSections(cluster.getId(), "갱신된 제목", "갱신된 요약", newSections, articleIds);

        // then — 섹션이 3개로 교체
        List<ClusterSummarySection> afterSecond =
                sectionRepository.findByNewsClusterIdOrderBySectionOrderAsc(cluster.getId());
        assertThat(afterSecond).hasSize(3);
        assertThat(afterSecond.get(0).getHeading()).isEqualTo("새 소제목 X");
        assertThat(afterSecond.get(1).getHeading()).isEqualTo("새 소제목 Y");
        assertThat(afterSecond.get(2).getHeading()).isEqualTo("새 소제목 Z");

        // 기존 섹션의 출처도 JPQL 벌크 DELETE로 삭제됨
        List<ClusterSummarySectionSource> oldSources =
                sectionSourceRepository.findByClusterSummarySectionId(oldSectionId);
        assertThat(oldSources).isEmpty();

        // 새 섹션의 출처 정상 저장
        List<ClusterSummarySectionSource> newSources =
                sectionSourceRepository.findByClusterSummarySectionId(afterSecond.get(0).getId());
        assertThat(newSources).hasSize(2);
    }

    @Test
    @DisplayName("잘못된 기사 인덱스는 스킵되고 유효한 출처만 저장된다")
    void markGenerated_invalidArticleIndex_skipped() {
        // given
        NewsArticle article1 = createAndSaveArticle();
        NewsCluster cluster = createAndSaveCluster(article1.getId(), 1);
        List<Long> articleIds = List.of(article1.getId());
        createClusterArticleMappings(cluster.getId(), articleIds);

        // index 1은 유효, index 5는 범위 초과
        List<SummaryResult.SectionItem> sections = List.of(
                createSectionItem("소제목", "본문 내용", List.of(1, 5))
        );

        // when
        persistenceService.markGeneratedWithSections(cluster.getId(), "제목", "요약", sections, articleIds);

        // then
        List<ClusterSummarySection> savedSections =
                sectionRepository.findByNewsClusterIdOrderBySectionOrderAsc(cluster.getId());
        assertThat(savedSections).hasSize(1);

        List<ClusterSummarySectionSource> sources =
                sectionSourceRepository.findByClusterSummarySectionId(savedSections.get(0).getId());
        assertThat(sources).hasSize(1);
        assertThat(sources.get(0).getNewsArticleId()).isEqualTo(article1.getId());
    }

    @Test
    @DisplayName("CAS 불일치 — 기사 집합 변경 시 StaleClusterException 발생")
    void markGenerated_membershipChanged_throwsStaleClusterException() {
        // given: 기사 2개로 클러스터 생성, 매핑은 1개만 등록 (불일치)
        NewsArticle article1 = createAndSaveArticle();
        NewsArticle article2 = createAndSaveArticle();
        NewsCluster cluster = createAndSaveCluster(article1.getId(), 1);
        createClusterArticleMappings(cluster.getId(), List.of(article1.getId()));

        List<Long> expectedArticleIds = List.of(article1.getId(), article2.getId());
        List<SummaryResult.SectionItem> sections = List.of(
                createSectionItem("소제목", "본문", List.of(1))
        );

        // when & then
        assertThatThrownBy(() ->
                persistenceService.markGeneratedWithSections(
                        cluster.getId(), "제목", "요약", sections, expectedArticleIds))
                .isInstanceOf(StaleClusterException.class);
    }

    @Test
    @DisplayName("CAS 불일치 — 개수는 같지만 기사 집합이 다르면 StaleClusterException 발생")
    void markGenerated_sameCountDifferentSet_throwsStaleClusterException() {
        // given: A,B로 요약했는데, 저장 시점에는 A,C로 교체됨
        NewsArticle articleA = createAndSaveArticle();
        NewsArticle articleB = createAndSaveArticle();
        NewsArticle articleC = createAndSaveArticle();
        NewsCluster cluster = createAndSaveCluster(articleA.getId(), 2);
        createClusterArticleMappings(cluster.getId(), List.of(articleA.getId(), articleC.getId()));

        List<Long> expectedArticleIds = List.of(articleA.getId(), articleB.getId());
        List<SummaryResult.SectionItem> sections = List.of(
                createSectionItem("소제목", "본문", List.of(1, 2))
        );

        // when & then
        assertThatThrownBy(() ->
                persistenceService.markGeneratedWithSections(
                        cluster.getId(), "제목", "요약", sections, expectedArticleIds))
                .isInstanceOf(StaleClusterException.class);
    }

    @Test
    @DisplayName("markGeneratedSingle — 기사 집합 변경 시 StaleClusterException 발생")
    void markGeneratedSingle_membershipChanged_throwsStaleClusterException() {
        // given: 단독으로 기대하지만 실제로는 기사가 2개 매핑됨
        NewsArticle article1 = createAndSaveArticle();
        NewsArticle article2 = createAndSaveArticle();
        NewsCluster cluster = createAndSaveCluster(article1.getId(), 2);
        createClusterArticleMappings(cluster.getId(), List.of(article1.getId(), article2.getId()));

        // when & then
        assertThatThrownBy(() ->
                persistenceService.markGeneratedSingle(
                        cluster.getId(), "제목", "요약", List.of(article1.getId())))
                .isInstanceOf(StaleClusterException.class);
    }

    private NewsCluster createAndSaveCluster(Long representativeArticleId, int articleCount) {
        float[] vector = {1.0f, 0.0f, 0.0f};
        NewsCluster cluster = NewsCluster.createSingle(vector, representativeArticleId, null, OffsetDateTime.now());
        ReflectionTestUtils.setField(cluster, "articleCount", articleCount);
        return clusterRepository.save(cluster);
    }

    private void createClusterArticleMappings(Long clusterId, List<Long> articleIds) {
        for (int i = 0; i < articleIds.size(); i++) {
            clusterArticleRepository.save(NewsClusterArticle.create(clusterId, articleIds.get(i), i, false));
        }
        clusterArticleRepository.flush();
    }

    private NewsArticle createAndSaveArticle() {
        NewsArticle article = NewsArticle.builder()
                .rawNewsArticleId(null)
                .publisherName("test")
                .title("테스트 기사")
                .content("테스트 본문")
                .originalUrl("https://example.com/test-" + java.util.UUID.randomUUID())
                .dedupKey("key-" + java.util.UUID.randomUUID())
                .build();
        return articleRepository.save(article);
    }

    private SummaryResult.SectionItem createSectionItem(String heading, String body,
                                                         List<Integer> sourceIndices) {
        SummaryResult.SectionItem item = new SummaryResult.SectionItem();
        ReflectionTestUtils.setField(item, "heading", heading);
        ReflectionTestUtils.setField(item, "body", body);
        ReflectionTestUtils.setField(item, "sourceArticleIndices", sourceIndices);
        return item;
    }
}
