package com.solv.wefin.domain.news.cluster.service;

import com.solv.wefin.domain.news.article.entity.NewsArticleTag;
import com.solv.wefin.domain.news.article.entity.NewsArticleTag.TagType;
import com.solv.wefin.domain.news.article.repository.NewsArticleRepository;
import com.solv.wefin.domain.news.article.repository.NewsArticleRepository.ArticleSourceProjection;
import com.solv.wefin.domain.news.article.repository.NewsArticleRepository.SourceProjection;
import com.solv.wefin.domain.news.article.repository.NewsArticleTagRepository;
import com.solv.wefin.domain.news.cluster.dto.ArticleSourceInfo;
import com.solv.wefin.domain.news.cluster.dto.SourceInfo;
import com.solv.wefin.domain.news.cluster.dto.StockInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ClusterTagAggregatorTest {

    @Mock private NewsArticleRepository newsArticleRepository;
    @Mock private NewsArticleTagRepository articleTagRepository;

    @InjectMocks private ClusterTagAggregator aggregator;

    // ---------- aggregateSources ----------

    @Test
    @DisplayName("aggregateSources — 같은 언론사는 1개만, 최대 3개까지")
    void aggregateSources_dedupAndCap() {
        Map<Long, List<Long>> clusterMap = Map.of(1L, List.of(10L, 20L, 30L, 40L, 50L));
        given(newsArticleRepository.findSourceInfoByIdIn(any())).willReturn(List.of(
                sourceProjection(10L, "매일경제", "url1"),
                sourceProjection(20L, "매일경제", "url2"),    // 같은 언론사 dedup
                sourceProjection(30L, "한경", "url3"),
                sourceProjection(40L, "연합", "url4"),
                sourceProjection(50L, "동아", "url5")          // 4번째 — 최대 3개 컷
        ));

        Map<Long, List<SourceInfo>> result = aggregator.aggregateSources(
                clusterMap, List.of(10L, 20L, 30L, 40L, 50L));

        assertThat(result.get(1L)).extracting(SourceInfo::publisherName)
                .containsExactly("매일경제", "한경", "연합");
        assertThat(result.get(1L)).hasSize(3);
    }

    @Test
    @DisplayName("aggregateSources — publisherName이 null이면 스킵")
    void aggregateSources_skipsNullPublisher() {
        Map<Long, List<Long>> clusterMap = Map.of(1L, List.of(10L, 20L));
        given(newsArticleRepository.findSourceInfoByIdIn(any())).willReturn(List.of(
                sourceProjection(10L, null, "url1"),
                sourceProjection(20L, "한경", "url2")
        ));

        Map<Long, List<SourceInfo>> result = aggregator.aggregateSources(
                clusterMap, List.of(10L, 20L));

        assertThat(result.get(1L)).extracting(SourceInfo::publisherName).containsExactly("한경");
    }

    // ---------- aggregateStocks (TagType.STOCK 쿼리) ----------

    @Test
    @DisplayName("aggregateStocks — TagType.STOCK으로 조회하고 code 기준 dedup")
    void aggregateStocks_queriesStockTypeAndDedupsByCode() {
        Map<Long, List<Long>> clusterMap = Map.of(1L, List.of(10L, 20L));
        given(articleTagRepository.findByNewsArticleIdInAndTagType(any(), eq(TagType.STOCK)))
                .willReturn(List.of(
                        stockTag(10L, "005930", "삼성전자"),
                        stockTag(20L, "005930", "삼성전자"),  // dedup 대상
                        stockTag(20L, "000660", "SK하이닉스")
                ));

        Map<Long, List<StockInfo>> result = aggregator.aggregateStocks(
                clusterMap, List.of(10L, 20L));

        assertThat(result.get(1L))
                .extracting(StockInfo::code)
                .containsExactly("005930", "000660");
    }

    // ---------- aggregateMarketTags (TagType.TOPIC 쿼리) ----------

    @Test
    @DisplayName("aggregateMarketTags — TagType.TOPIC으로 조회하고 name 기준 dedup")
    void aggregateMarketTags_queriesTopicTypeAndReturnsNames() {
        Map<Long, List<Long>> clusterMap = Map.of(1L, List.of(10L, 20L));
        given(articleTagRepository.findByNewsArticleIdInAndTagType(any(), eq(TagType.TOPIC)))
                .willReturn(List.of(
                        topicTag(10L, "EARNINGS", "실적"),
                        topicTag(20L, "EARNINGS", "실적"),   // dedup
                        topicTag(20L, "AI", "AI")
                ));

        Map<Long, List<String>> result = aggregator.aggregateMarketTags(
                clusterMap, List.of(10L, 20L));

        assertThat(result.get(1L)).containsExactly("실적", "AI");
    }

    @Test
    @DisplayName("aggregateMarketTags — TagType.STOCK은 조회하지 않는다 (분기 회귀 방지)")
    void aggregateMarketTags_doesNotQueryStockType() {
        Map<Long, List<Long>> clusterMap = Map.of(1L, List.of(10L));
        given(articleTagRepository.findByNewsArticleIdInAndTagType(any(), eq(TagType.TOPIC)))
                .willReturn(List.of());

        aggregator.aggregateMarketTags(clusterMap, List.of(10L));

        org.mockito.Mockito.verify(articleTagRepository)
                .findByNewsArticleIdInAndTagType(any(), eq(TagType.TOPIC));
        org.mockito.Mockito.verify(articleTagRepository, org.mockito.Mockito.never())
                .findByNewsArticleIdInAndTagType(any(), eq(TagType.STOCK));
    }

    // ---------- aggregateDetailSources ----------

    @Test
    @DisplayName("aggregateDetailSources — 섹션 출처 기사가 앞쪽에 배치되고 publisher dedup + 최대 3개")
    void aggregateDetailSources_prioritizesSectionArticlesAndDedupsAndCaps() {
        // 기사 순서: 10, 20, 30, 40, 50 (20과 40만 섹션 출처)
        List<Long> articleIds = List.of(10L, 20L, 30L, 40L, 50L);
        Set<Long> sectionSourceIds = Set.of(20L, 40L);

        given(newsArticleRepository.findArticleSourceInfoByIdIn(articleIds)).willReturn(List.of(
                articleSourceProjection(10L, "기사A", "매일경제", "url1"),
                articleSourceProjection(20L, "기사B", "한경", "url2"),       // 섹션 출처
                articleSourceProjection(30L, "기사C", "한경", "url3"),       // dedup 대상
                articleSourceProjection(40L, "기사D", "연합", "url4"),       // 섹션 출처
                articleSourceProjection(50L, "기사E", "동아", "url5")        // 4번째로 들어가 컷
        ));

        List<ArticleSourceInfo> result = aggregator.aggregateDetailSources(articleIds, sectionSourceIds);

        // 정책 검증:
        // 1) 섹션 출처(20, 40)가 비섹션 출처(10)보다 먼저
        // 2) 30L은 20L과 같은 "한경"이라 dedup
        // 3) 최대 3개
        assertThat(result).hasSize(3);
        assertThat(result).extracting(ArticleSourceInfo::articleId)
                .containsExactly(20L, 40L, 10L);
        assertThat(result).extracting(ArticleSourceInfo::publisherName)
                .containsExactly("한경", "연합", "매일경제");
    }

    @Test
    @DisplayName("aggregateDetailSources — projectionMap에 없는 id는 제외된다 (크롤 실패/삭제)")
    void aggregateDetailSources_missingProjection_isExcluded() {
        List<Long> articleIds = List.of(10L, 20L, 30L);
        // 20L은 삭제되어 projection에서 누락
        given(newsArticleRepository.findArticleSourceInfoByIdIn(articleIds)).willReturn(List.of(
                articleSourceProjection(10L, "기사A", "매일경제", "url1"),
                articleSourceProjection(30L, "기사C", "한경", "url3")
        ));

        List<ArticleSourceInfo> result = aggregator.aggregateDetailSources(articleIds, Set.of());

        assertThat(result).extracting(ArticleSourceInfo::articleId).containsExactly(10L, 30L);
    }

    // ---------- multi-cluster 비간섭 ----------

    @Test
    @DisplayName("aggregateSources — 여러 클러스터가 있을 때 각자의 기사만 집계한다 (간섭 없음)")
    void aggregateSources_multipleClusters_independentResults() {
        Map<Long, List<Long>> clusterMap = Map.of(
                1L, List.of(10L, 20L),
                2L, List.of(30L, 40L));
        given(newsArticleRepository.findSourceInfoByIdIn(any())).willReturn(List.of(
                sourceProjection(10L, "매일경제", "u1"),
                sourceProjection(20L, "한경", "u2"),
                sourceProjection(30L, "연합", "u3"),
                sourceProjection(40L, "매일경제", "u4")  // 클러스터 1의 매일경제와 같은 이름이지만 클러스터 2에 속함
        ));

        Map<Long, List<SourceInfo>> result = aggregator.aggregateSources(
                clusterMap, List.of(10L, 20L, 30L, 40L));

        // 클러스터 1: 매일경제 + 한경
        assertThat(result.get(1L)).extracting(SourceInfo::publisherName)
                .containsExactly("매일경제", "한경");
        // 클러스터 2: 연합 + 매일경제 (클러스터 1의 매일경제에 영향받지 않음)
        assertThat(result.get(2L)).extracting(SourceInfo::publisherName)
                .containsExactly("연합", "매일경제");
    }

    // ---------- putIfAbsent 고정 동작 ----------

    @Test
    @DisplayName("aggregateStocks — 같은 code에 다른 name이 오면 먼저 본 name을 고정한다 (putIfAbsent)")
    void aggregateStocks_sameCodeDifferentName_keepsFirstSeenName() {
        Map<Long, List<Long>> clusterMap = Map.of(1L, List.of(10L, 20L));
        // articleId 10이 먼저 순회되므로 "삼성전자"가 고정
        given(articleTagRepository.findByNewsArticleIdInAndTagType(any(), eq(TagType.STOCK)))
                .willReturn(List.of(
                        stockTag(10L, "005930", "삼성전자"),
                        stockTag(20L, "005930", "삼전")
                ));

        Map<Long, List<StockInfo>> result = aggregator.aggregateStocks(
                clusterMap, List.of(10L, 20L));

        assertThat(result.get(1L)).hasSize(1);
        assertThat(result.get(1L).get(0).name()).isEqualTo("삼성전자");
    }

    // ---------- 단건 오버로드 ----------

    @Test
    @DisplayName("aggregateStocksForCluster — 단건 편의 오버로드")
    void aggregateStocksForCluster_returnsDedupedStocks() {
        given(articleTagRepository.findByNewsArticleIdInAndTagType(eq(List.of(10L, 20L)), eq(TagType.STOCK)))
                .willReturn(List.of(
                        stockTag(10L, "005930", "삼성전자"),
                        stockTag(20L, "000660", "SK하이닉스")
                ));

        List<StockInfo> result = aggregator.aggregateStocksForCluster(List.of(10L, 20L));

        assertThat(result).extracting(StockInfo::code).containsExactly("005930", "000660");
    }

    @Test
    @DisplayName("aggregateMarketTagsForCluster — 단건 편의 오버로드, 빈 입력은 빈 리스트")
    void aggregateMarketTagsForCluster_emptyInput_returnsEmpty() {
        assertThat(aggregator.aggregateMarketTagsForCluster(List.of())).isEmpty();
        // 빈 입력이면 repository 호출도 없음
        org.mockito.Mockito.verifyNoInteractions(articleTagRepository);
    }

    @Test
    @DisplayName("aggregateDetailSources — 섹션 출처 없음 시 articleIds 순서 유지")
    void aggregateDetailSources_noSectionSources_preservesOrder() {
        List<Long> articleIds = List.of(10L, 20L);
        given(newsArticleRepository.findArticleSourceInfoByIdIn(articleIds)).willReturn(List.of(
                articleSourceProjection(10L, "기사A", "매일경제", "url1"),
                articleSourceProjection(20L, "기사B", "한경", "url2")
        ));

        List<ArticleSourceInfo> result = aggregator.aggregateDetailSources(articleIds, Set.of());

        assertThat(result).extracting(ArticleSourceInfo::articleId).containsExactly(10L, 20L);
    }

    // ---------- helpers ----------

    private SourceProjection sourceProjection(Long id, String publisher, String url) {
        return new SourceProjection() {
            public Long getId() { return id; }
            public String getPublisherName() { return publisher; }
            public String getOriginalUrl() { return url; }
        };
    }

    private ArticleSourceProjection articleSourceProjection(Long id, String title, String publisher, String url) {
        return new ArticleSourceProjection() {
            public Long getId() { return id; }
            public String getTitle() { return title; }
            public String getPublisherName() { return publisher; }
            public String getOriginalUrl() { return url; }
        };
    }

    private NewsArticleTag stockTag(Long articleId, String code, String name) {
        return tag(articleId, TagType.STOCK, code, name);
    }

    private NewsArticleTag topicTag(Long articleId, String code, String name) {
        return tag(articleId, TagType.TOPIC, code, name);
    }

    private NewsArticleTag tag(Long articleId, TagType type, String code, String name) {
        return NewsArticleTag.builder()
                .newsArticleId(articleId)
                .tagType(type)
                .tagCode(code)
                .tagName(name)
                .build();
    }
}
