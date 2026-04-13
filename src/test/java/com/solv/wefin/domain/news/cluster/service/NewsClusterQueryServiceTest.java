package com.solv.wefin.domain.news.cluster.service;

import com.solv.wefin.domain.news.article.entity.NewsArticleTag;
import com.solv.wefin.domain.news.article.entity.NewsArticleTag.TagType;
import com.solv.wefin.domain.news.article.repository.NewsArticleRepository;
import com.solv.wefin.domain.news.article.repository.NewsArticleRepository.ArticleSourceProjection;
import com.solv.wefin.domain.news.article.repository.NewsArticleRepository.SourceProjection;
import com.solv.wefin.domain.news.cluster.entity.ClusterSuggestedQuestion;
import com.solv.wefin.domain.news.cluster.entity.ClusterSummarySection;
import com.solv.wefin.domain.news.cluster.entity.ClusterSummarySectionSource;
import com.solv.wefin.domain.news.article.repository.NewsArticleTagRepository;
import com.solv.wefin.domain.news.cluster.entity.NewsCluster;
import com.solv.wefin.domain.news.cluster.entity.NewsCluster.ClusterStatus;
import com.solv.wefin.domain.news.cluster.entity.NewsCluster.SummaryStatus;
import com.solv.wefin.domain.news.cluster.entity.NewsClusterArticle;
import com.solv.wefin.domain.news.cluster.entity.UserNewsClusterRead;
import com.solv.wefin.domain.news.cluster.repository.ClusterSuggestedQuestionRepository;
import com.solv.wefin.domain.news.cluster.repository.ClusterSummarySectionRepository;
import com.solv.wefin.domain.news.cluster.repository.ClusterSummarySectionSourceRepository;
import com.solv.wefin.domain.news.cluster.repository.NewsClusterArticleRepository;
import com.solv.wefin.domain.news.cluster.repository.NewsClusterRepository;
import com.solv.wefin.domain.news.cluster.repository.UserNewsClusterFeedbackRepository;
import com.solv.wefin.domain.news.cluster.repository.UserNewsClusterReadRepository;
import com.solv.wefin.domain.news.cluster.service.NewsClusterQueryService.ClusterDetailResult;
import com.solv.wefin.domain.news.cluster.service.NewsClusterQueryService.ClusterFeedResult;
import com.solv.wefin.global.error.BusinessException;
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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NewsClusterQueryServiceTest {

    @Mock private NewsClusterRepository newsClusterRepository;
    @Mock private NewsClusterArticleRepository clusterArticleRepository;
    @Mock private NewsArticleRepository newsArticleRepository;
    @Mock private NewsArticleTagRepository articleTagRepository;
    @Mock private UserNewsClusterReadRepository readRepository;
    @Mock private UserNewsClusterFeedbackRepository feedbackRepository;
    @Mock private ClusterSummarySectionRepository sectionRepository;
    @Mock private ClusterSuggestedQuestionRepository questionRepository;
    @Mock private ClusterSummarySectionSourceRepository sectionSourceRepository;

    private NewsClusterQueryService queryService;

    @BeforeEach
    void setUp() {
        // aggregator는 실제 구현으로 wiring (내부에서 NewsArticleRepository/TagRepository 사용)
        ClusterTagAggregator tagAggregator = new ClusterTagAggregator(newsArticleRepository, articleTagRepository);
        queryService = new NewsClusterQueryService(
                newsClusterRepository, clusterArticleRepository,
                newsArticleRepository, readRepository, feedbackRepository,
                questionRepository, sectionRepository, sectionSourceRepository,
                tagAggregator);
    }

    @Test
    @DisplayName("첫 페이지 조회 — STOCK/TOPIC 분기별 쿼리 + relatedStocks/marketTags 반영")
    void getFeed_firstPage() {
        NewsCluster cluster = createCluster(1L, "삼성전자 실적 호조", "반도체 부문 회복...",
                OffsetDateTime.now(), 3);

        given(newsClusterRepository.findForFeedFirstPageByPublishedAt(any(), any(), any()))
                .willReturn(List.of(cluster));
        given(clusterArticleRepository.findByNewsClusterIdIn(any()))
                .willReturn(List.of(NewsClusterArticle.create(1L, 100L, 0, false)));
        given(newsArticleRepository.findSourceInfoByIdIn(any()))
                .willReturn(List.of(createSourceProjection(100L, "매일경제", "https://example.com/100")));
        // STOCK과 TOPIC은 분기별로 stub — 매퍼 연결 실수 방어
        given(articleTagRepository.findByNewsArticleIdInAndTagType(any(), eq(TagType.STOCK)))
                .willReturn(List.of(createTag(100L, TagType.STOCK, "005930", "삼성전자")));
        given(articleTagRepository.findByNewsArticleIdInAndTagType(any(), eq(TagType.TOPIC)))
                .willReturn(List.of(createTag(100L, TagType.TOPIC, "EARNINGS", "실적")));

        ClusterFeedResult result = queryService.getFeed(null, null, 10, null, null, null);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).title()).isEqualTo("삼성전자 실적 호조");
        assertThat(result.items().get(0).sources()).hasSize(1);
        assertThat(result.items().get(0).relatedStocks()).hasSize(1);
        assertThat(result.items().get(0).relatedStocks().get(0).code()).isEqualTo("005930");
        assertThat(result.items().get(0).relatedStocks().get(0).name()).isEqualTo("삼성전자");
        assertThat(result.items().get(0).marketTags()).containsExactly("실적");
        assertThat(result.items().get(0).isRead()).isFalse();
        assertThat(result.hasNext()).isFalse();

        // QueryService → aggregator 경로에서 STOCK/TOPIC 쿼리 모두 발생했는지 검증
        verify(articleTagRepository).findByNewsArticleIdInAndTagType(any(), eq(TagType.STOCK));
        verify(articleTagRepository).findByNewsArticleIdInAndTagType(any(), eq(TagType.TOPIC));
    }

    @Test
    @DisplayName("hasNext — pageSize+1 건이면 true")
    void getFeed_hasNext() {
        NewsCluster c1 = createCluster(1L, "title1", "summary1", OffsetDateTime.now(), 2);
        NewsCluster c2 = createCluster(2L, "title2", "summary2", OffsetDateTime.now().minusMinutes(1), 1);

        // pageSize=1이면 2건 조회 → hasNext=true
        given(newsClusterRepository.findForFeedFirstPageByPublishedAt(any(), any(), any()))
                .willReturn(List.of(c1, c2));
        given(clusterArticleRepository.findByNewsClusterIdIn(any())).willReturn(List.of());

        ClusterFeedResult result = queryService.getFeed(null, null, 1, null, null, null);

        assertThat(result.items()).hasSize(1);
        assertThat(result.hasNext()).isTrue();
        assertThat(result.nextCursorId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("읽음 상태 — userId가 있으면 isRead 반영")
    void getFeed_withReadStatus() {
        UUID userId = UUID.randomUUID();
        NewsCluster cluster = createCluster(1L, "title", "summary", OffsetDateTime.now(), 1);

        given(newsClusterRepository.findForFeedFirstPageByPublishedAt(any(), any(), any()))
                .willReturn(List.of(cluster));
        given(clusterArticleRepository.findByNewsClusterIdIn(any())).willReturn(List.of());
        given(readRepository.findByUserIdAndNewsClusterIdIn(eq(userId), any()))
                .willReturn(List.of(UserNewsClusterRead.create(userId, 1L)));

        ClusterFeedResult result = queryService.getFeed(null, null, 10, userId, null, null);

        assertThat(result.items().get(0).isRead()).isTrue();
    }

    @Test
    @DisplayName("빈 결과")
    void getFeed_empty() {
        given(newsClusterRepository.findForFeedFirstPageByPublishedAt(any(), any(), any()))
                .willReturn(List.of());

        ClusterFeedResult result = queryService.getFeed(null, null, 10, null, null, null);

        assertThat(result.items()).isEmpty();
        assertThat(result.hasNext()).isFalse();
    }

    @Test
    @DisplayName("커서 기반 다음 페이지 조회")
    void getFeed_withCursor() {
        OffsetDateTime cursor = OffsetDateTime.now().minusHours(1);
        NewsCluster cluster = createCluster(5L, "older", "summary", cursor.minusMinutes(10), 1);

        given(newsClusterRepository.findForFeedAfterCursorByPublishedAt(any(), any(), eq(cursor), eq(10L), any()))
                .willReturn(List.of(cluster));
        given(clusterArticleRepository.findByNewsClusterIdIn(any())).willReturn(List.of());

        ClusterFeedResult result = queryService.getFeed(cursor, 10L, 10, null, null, null);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).clusterId()).isEqualTo(5L);
    }

    // --- getDetail 테스트 ---

    @Test
    @DisplayName("상세 조회 — 섹션 + 출처가 있는 클러스터")
    void getDetail_withSections() {
        NewsCluster cluster = createCluster(1L, "제목", "요약", OffsetDateTime.now(), 3);

        given(newsClusterRepository.findById(1L)).willReturn(Optional.of(cluster));
        given(clusterArticleRepository.findByNewsClusterId(1L))
                .willReturn(List.of(
                        NewsClusterArticle.create(1L, 100L, 0, false),
                        NewsClusterArticle.create(1L, 200L, 1, false)));
        given(newsArticleRepository.findArticleSourceInfoByIdIn(any()))
                .willReturn(List.of(
                        createArticleSourceProjection(100L, "기사A", "매일경제", "https://a.com"),
                        createArticleSourceProjection(200L, "기사B", "한경", "https://b.com")));

        // 섹션 mock
        ClusterSummarySection section1 = createSection(10L, 1L, 0, "소제목1", "본문1");
        ClusterSummarySection section2 = createSection(20L, 1L, 1, "소제목2", "본문2");
        given(sectionRepository.findByNewsClusterIdOrderBySectionOrderAsc(1L))
                .willReturn(List.of(section1, section2));
        given(sectionSourceRepository.findByClusterSummarySectionIdIn(List.of(10L, 20L)))
                .willReturn(List.of(
                        ClusterSummarySectionSource.create(10L, 100L),
                        ClusterSummarySectionSource.create(10L, 200L),
                        ClusterSummarySectionSource.create(20L, 200L)));

        ClusterDetailResult result = queryService.getDetail(1L, null);

        assertThat(result.sections()).hasSize(2);
        assertThat(result.sections().get(0).heading()).isEqualTo("소제목1");
        assertThat(result.sections().get(0).sourceCount()).isEqualTo(2);
        assertThat(result.sections().get(0).sources()).hasSize(2);
        assertThat(result.sections().get(1).heading()).isEqualTo("소제목2");
        assertThat(result.sections().get(1).sourceCount()).isEqualTo(1);
        assertThat(result.sources()).isNotEmpty();
    }

    @Test
    @DisplayName("상세 조회 — 상세 출처는 섹션 출처 기사 우선, publisher dedup, 최대 3개")
    void getDetail_sourcesPrioritizeSectionArticles() {
        NewsCluster cluster = createCluster(1L, "제목", "요약", OffsetDateTime.now(), 5);

        given(newsClusterRepository.findById(1L)).willReturn(Optional.of(cluster));
        // 기사 순서: 100, 200, 300, 400, 500 — 200과 400만 섹션 출처
        given(clusterArticleRepository.findByNewsClusterId(1L))
                .willReturn(List.of(
                        NewsClusterArticle.create(1L, 100L, 0, false),
                        NewsClusterArticle.create(1L, 200L, 1, false),
                        NewsClusterArticle.create(1L, 300L, 2, false),
                        NewsClusterArticle.create(1L, 400L, 3, false),
                        NewsClusterArticle.create(1L, 500L, 4, false)));

        given(newsArticleRepository.findArticleSourceInfoByIdIn(any()))
                .willReturn(List.of(
                        createArticleSourceProjection(100L, "기사A", "매일경제", "https://a.com"),
                        createArticleSourceProjection(200L, "기사B", "한경", "https://b.com"),     // 섹션 출처
                        createArticleSourceProjection(300L, "기사C", "한경", "https://c.com"),     // dedup 대상
                        createArticleSourceProjection(400L, "기사D", "연합", "https://d.com"),     // 섹션 출처
                        createArticleSourceProjection(500L, "기사E", "동아", "https://e.com")));   // cap 컷

        // 섹션 두 개 — 각각 200, 400을 출처로 가짐
        ClusterSummarySection s1 = createSection(10L, 1L, 0, "소제목1", "본문1");
        ClusterSummarySection s2 = createSection(20L, 1L, 1, "소제목2", "본문2");
        given(sectionRepository.findByNewsClusterIdOrderBySectionOrderAsc(1L))
                .willReturn(List.of(s1, s2));
        given(sectionSourceRepository.findByClusterSummarySectionIdIn(List.of(10L, 20L)))
                .willReturn(List.of(
                        ClusterSummarySectionSource.create(10L, 200L),
                        ClusterSummarySectionSource.create(20L, 400L)));

        ClusterDetailResult result = queryService.getDetail(1L, null);

        // 정책:
        // 1) 섹션 출처(200, 400)가 비섹션 출처(100)보다 앞
        // 2) 300은 200과 같은 "한경"이라 dedup
        // 3) 최대 3개 (500은 컷)
        assertThat(result.sources()).hasSize(3);
        assertThat(result.sources()).extracting(s -> s.articleId())
                .containsExactly(200L, 400L, 100L);
        assertThat(result.sources()).extracting(s -> s.publisherName())
                .containsExactly("한경", "연합", "매일경제");
    }

    @Test
    @DisplayName("상세 조회 — 섹션이 없는 클러스터")
    void getDetail_noSections() {
        NewsCluster cluster = createCluster(1L, "제목", "요약", OffsetDateTime.now(), 2);

        given(newsClusterRepository.findById(1L)).willReturn(Optional.of(cluster));
        given(clusterArticleRepository.findByNewsClusterId(1L)).willReturn(List.of());
        given(sectionRepository.findByNewsClusterIdOrderBySectionOrderAsc(1L)).willReturn(List.of());

        ClusterDetailResult result = queryService.getDetail(1L, null);

        assertThat(result.clusterId()).isEqualTo(1L);
        assertThat(result.title()).isEqualTo("제목");
        assertThat(result.sections()).isEmpty();
        assertThat(result.isRead()).isFalse();
    }

    @Test
    @DisplayName("상세 조회 — INACTIVE 클러스터는 예외")
    void getDetail_inactive_throwsException() {
        NewsCluster cluster = createCluster(1L, "제목", "요약", OffsetDateTime.now(), 1);
        ReflectionTestUtils.setField(cluster, "status", ClusterStatus.INACTIVE);

        given(newsClusterRepository.findById(1L)).willReturn(Optional.of(cluster));

        assertThatThrownBy(() -> queryService.getDetail(1L, null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("상세 조회 — PENDING 상태 클러스터는 예외")
    void getDetail_pending_throwsException() {
        NewsCluster cluster = createCluster(1L, "제목", "요약", OffsetDateTime.now(), 1);
        ReflectionTestUtils.setField(cluster, "summaryStatus", SummaryStatus.PENDING);

        given(newsClusterRepository.findById(1L)).willReturn(Optional.of(cluster));

        assertThatThrownBy(() -> queryService.getDetail(1L, null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("상세 조회 — suggestedQuestions가 question_order 순서대로 응답에 포함된다")
    void getDetail_suggestedQuestionsInOrder() {
        NewsCluster cluster = createCluster(1L, "제목", "요약", OffsetDateTime.now(), 2);

        given(newsClusterRepository.findById(1L)).willReturn(Optional.of(cluster));
        given(clusterArticleRepository.findByNewsClusterId(1L)).willReturn(List.of());
        given(sectionRepository.findByNewsClusterIdOrderBySectionOrderAsc(1L)).willReturn(List.of());

        // Repository는 메서드 이름(OrderByQuestionOrder) 기반으로 정렬된 결과를 반환한다고 가정
        ClusterSuggestedQuestion q0 = ClusterSuggestedQuestion.create(1L, 0, "첫 번째 질문");
        ClusterSuggestedQuestion q1 = ClusterSuggestedQuestion.create(1L, 1, "두 번째 질문");
        ClusterSuggestedQuestion q2 = ClusterSuggestedQuestion.create(1L, 2, "세 번째 질문");
        given(questionRepository.findByNewsClusterIdOrderByQuestionOrder(1L))
                .willReturn(List.of(q0, q1, q2));

        ClusterDetailResult result = queryService.getDetail(1L, null);

        assertThat(result.suggestedQuestions())
                .containsExactly("첫 번째 질문", "두 번째 질문", "세 번째 질문");
    }

    @Test
    @DisplayName("상세 조회 — 추천 질문이 없으면 빈 리스트")
    void getDetail_noQuestions_returnsEmpty() {
        NewsCluster cluster = createCluster(1L, "제목", "요약", OffsetDateTime.now(), 2);

        given(newsClusterRepository.findById(1L)).willReturn(Optional.of(cluster));
        given(clusterArticleRepository.findByNewsClusterId(1L)).willReturn(List.of());
        given(sectionRepository.findByNewsClusterIdOrderBySectionOrderAsc(1L)).willReturn(List.of());
        given(questionRepository.findByNewsClusterIdOrderByQuestionOrder(1L)).willReturn(List.of());

        ClusterDetailResult result = queryService.getDetail(1L, null);

        assertThat(result.suggestedQuestions()).isEmpty();
    }

    @Test
    @DisplayName("상세 조회 — 존재하지 않는 clusterId는 예외")
    void getDetail_notFound_throwsException() {
        given(newsClusterRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> queryService.getDetail(999L, null))
                .isInstanceOf(BusinessException.class);
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

    private ClusterSummarySection createSection(long id, long clusterId, int order,
                                                 String heading, String body) {
        ClusterSummarySection section = ClusterSummarySection.create(clusterId, order, heading, body);
        ReflectionTestUtils.setField(section, "id", id);
        return section;
    }

    private ArticleSourceProjection createArticleSourceProjection(long id, String title,
                                                                    String publisherName, String url) {
        return new ArticleSourceProjection() {
            @Override public Long getId() { return id; }
            @Override public String getTitle() { return title; }
            @Override public String getPublisherName() { return publisherName; }
            @Override public String getOriginalUrl() { return url; }
        };
    }
}
