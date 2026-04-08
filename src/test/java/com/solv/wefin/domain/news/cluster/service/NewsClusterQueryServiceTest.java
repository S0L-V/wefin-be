package com.solv.wefin.domain.news.cluster.service;

import com.solv.wefin.domain.news.article.entity.NewsArticleTag;
import com.solv.wefin.domain.news.article.entity.NewsArticleTag.TagType;
import com.solv.wefin.domain.news.article.repository.NewsArticleRepository;
import com.solv.wefin.domain.news.article.repository.NewsArticleRepository.SourceProjection;
import com.solv.wefin.domain.news.article.repository.NewsArticleTagRepository;
import com.solv.wefin.domain.news.cluster.entity.NewsCluster;
import com.solv.wefin.domain.news.cluster.entity.NewsCluster.ClusterStatus;
import com.solv.wefin.domain.news.cluster.entity.NewsCluster.SummaryStatus;
import com.solv.wefin.domain.news.cluster.entity.NewsClusterArticle;
import com.solv.wefin.domain.news.cluster.entity.UserNewsClusterRead;
import com.solv.wefin.domain.news.cluster.repository.NewsClusterArticleRepository;
import com.solv.wefin.domain.news.cluster.repository.NewsClusterRepository;
import com.solv.wefin.domain.news.cluster.repository.UserNewsClusterReadRepository;
import com.solv.wefin.domain.news.cluster.service.NewsClusterQueryService.ClusterFeedResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class NewsClusterQueryServiceTest {

    @Mock private NewsClusterRepository newsClusterRepository;
    @Mock private NewsClusterArticleRepository clusterArticleRepository;
    @Mock private NewsArticleRepository newsArticleRepository;
    @Mock private NewsArticleTagRepository articleTagRepository;
    @Mock private UserNewsClusterReadRepository readRepository;

    private NewsClusterQueryService queryService;

    @BeforeEach
    void setUp() {
        queryService = new NewsClusterQueryService(
                newsClusterRepository, clusterArticleRepository,
                newsArticleRepository, articleTagRepository, readRepository);
    }

    @Test
    @DisplayName("첫 페이지 조회 — 클러스터 목록을 반환한다")
    void getFeed_firstPage() {
        NewsCluster cluster = createCluster(1L, "삼성전자 실적 호조", "반도체 부문 회복...",
                OffsetDateTime.now(), 3);

        given(newsClusterRepository.findForFeedFirstPage(any(), any(), any()))
                .willReturn(List.of(cluster));
        given(clusterArticleRepository.findByNewsClusterIdIn(any()))
                .willReturn(List.of(NewsClusterArticle.create(1L, 100L, 0, false)));
        given(newsArticleRepository.findSourceInfoByIdIn(any()))
                .willReturn(List.of(createSourceProjection(100L, "매일경제", "https://example.com/100")));
        given(articleTagRepository.findByNewsArticleIdInAndTagType(any(), any()))
                .willReturn(List.of(createTag(100L, TagType.STOCK, "005930", "삼성전자")));

        ClusterFeedResult result = queryService.getFeed(null, null, 10, null);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).title()).isEqualTo("삼성전자 실적 호조");
        assertThat(result.items().get(0).sourceCount()).isEqualTo(3);
        assertThat(result.items().get(0).sources()).hasSize(1);
        assertThat(result.items().get(0).relatedStocks()).containsExactly("삼성전자");
        assertThat(result.items().get(0).isRead()).isFalse();
        assertThat(result.hasNext()).isFalse();
    }

    @Test
    @DisplayName("hasNext — pageSize+1 건이면 true")
    void getFeed_hasNext() {
        NewsCluster c1 = createCluster(1L, "title1", "summary1", OffsetDateTime.now(), 2);
        NewsCluster c2 = createCluster(2L, "title2", "summary2", OffsetDateTime.now().minusMinutes(1), 1);

        // pageSize=1이면 2건 조회 → hasNext=true
        given(newsClusterRepository.findForFeedFirstPage(any(), any(), any()))
                .willReturn(List.of(c1, c2));
        given(clusterArticleRepository.findByNewsClusterIdIn(any())).willReturn(List.of());

        ClusterFeedResult result = queryService.getFeed(null, null, 1, null);

        assertThat(result.items()).hasSize(1);
        assertThat(result.hasNext()).isTrue();
        assertThat(result.nextCursorId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("읽음 상태 — userId가 있으면 isRead 반영")
    void getFeed_withReadStatus() {
        UUID userId = UUID.randomUUID();
        NewsCluster cluster = createCluster(1L, "title", "summary", OffsetDateTime.now(), 1);

        given(newsClusterRepository.findForFeedFirstPage(any(), any(), any()))
                .willReturn(List.of(cluster));
        given(clusterArticleRepository.findByNewsClusterIdIn(any())).willReturn(List.of());
        given(readRepository.findByUserIdAndNewsClusterIdIn(eq(userId), any()))
                .willReturn(List.of(UserNewsClusterRead.create(userId, 1L)));

        ClusterFeedResult result = queryService.getFeed(null, null, 10, userId);

        assertThat(result.items().get(0).isRead()).isTrue();
    }

    @Test
    @DisplayName("빈 결과")
    void getFeed_empty() {
        given(newsClusterRepository.findForFeedFirstPage(any(), any(), any()))
                .willReturn(List.of());

        ClusterFeedResult result = queryService.getFeed(null, null, 10, null);

        assertThat(result.items()).isEmpty();
        assertThat(result.hasNext()).isFalse();
    }

    @Test
    @DisplayName("커서 기반 다음 페이지 조회")
    void getFeed_withCursor() {
        OffsetDateTime cursor = OffsetDateTime.now().minusHours(1);
        NewsCluster cluster = createCluster(5L, "older", "summary", cursor.minusMinutes(10), 1);

        given(newsClusterRepository.findForFeedAfterCursor(any(), any(), eq(cursor), eq(10L), any()))
                .willReturn(List.of(cluster));
        given(clusterArticleRepository.findByNewsClusterIdIn(any())).willReturn(List.of());

        ClusterFeedResult result = queryService.getFeed(cursor, 10L, 10, null);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).clusterId()).isEqualTo(5L);
    }

    private NewsCluster createCluster(long id, String title, String summary,
                                       OffsetDateTime publishedAt, int articleCount) {
        NewsCluster cluster = NewsCluster.builder()
                .clusterType(NewsCluster.ClusterType.GENERAL)
                .centroidVector(new float[]{1.0f})
                .build();
        ReflectionTestUtils.setField(cluster, "id", id);
        ReflectionTestUtils.setField(cluster, "title", title);
        ReflectionTestUtils.setField(cluster, "summary", summary);
        ReflectionTestUtils.setField(cluster, "publishedAt", publishedAt);
        ReflectionTestUtils.setField(cluster, "articleCount", articleCount);
        ReflectionTestUtils.setField(cluster, "status", ClusterStatus.ACTIVE);
        ReflectionTestUtils.setField(cluster, "summaryStatus", SummaryStatus.GENERATED);
        return cluster;
    }

    private SourceProjection createSourceProjection(long id, String publisherName, String url) {
        return new SourceProjection() {
            @Override public Long getId() { return id; }
            @Override public String getPublisherName() { return publisherName; }
            @Override public String getOriginalUrl() { return url; }
        };
    }

    private NewsArticleTag createTag(long articleId, TagType type, String code, String name) {
        return NewsArticleTag.builder()
                .newsArticleId(articleId)
                .tagType(type)
                .tagCode(code)
                .tagName(name)
                .build();
    }
}
