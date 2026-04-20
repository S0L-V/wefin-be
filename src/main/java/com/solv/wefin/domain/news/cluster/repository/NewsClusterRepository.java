package com.solv.wefin.domain.news.cluster.repository;

import com.solv.wefin.domain.news.cluster.entity.NewsCluster;
import com.solv.wefin.domain.news.cluster.entity.NewsCluster.ClusterStatus;
import com.solv.wefin.domain.news.cluster.entity.NewsCluster.SummaryStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface NewsClusterRepository extends JpaRepository<NewsCluster, Long> {

    /**
     * 클러스터 row에 비관적 쓰기 락을 걸고 조회한다
     *
     * 같은 클러스터에 대한 요약 저장이 병렬로 실행될 때(예: 배치가 다중 인스턴스에서
     * 동시 실행되거나 앞선 배치가 길어져 다음 실행 주기와 겹치는 경우), delete-then-insert 시
     * question_order unique 제약 충돌을 방지한다. verifyArticlesUnchanged CAS는 기사
     * 집합 변경만 감지하므로, 같은 articleIds로 동시 진행되는 경우의 보호는 이 락이
     * 담당한다
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM NewsCluster c WHERE c.id = :id")
    Optional<NewsCluster> findByIdForUpdate(@Param("id") Long id);

    /**
     * 특정 상태의 클러스터 목록을 조회한다.
     */
    List<NewsCluster> findByStatus(ClusterStatus status);

    /**
     * 주어진 ID 중 노출 가능한(특정 상태 + 요약 완료 상태) 클러스터만 조회한다.
     *
     * 금융 동향 출처 카드 등 외부 노출용 조회에서 INACTIVE/요약 미완료 클러스터를
     * 자동으로 걸러낼 때 사용한다
     */
    List<NewsCluster> findByIdInAndStatusAndSummaryStatusIn(
            java.util.Collection<Long> ids,
            ClusterStatus status,
            java.util.Collection<SummaryStatus> summaryStatuses);

    Optional<NewsCluster> findByIdAndStatusAndSummaryStatusIn(
            Long id,
            ClusterStatus status,
            java.util.Collection<SummaryStatus> summaryStatuses);

    /**
     * 특정 상태이면서, 마지막 갱신 시각이 기준 시각 이전인 클러스터를 조회한다.
     */
    List<NewsCluster> findByStatusAndUpdatedAtBefore(ClusterStatus status, OffsetDateTime before);

    /**
     * ACTIVE 클러스터 중 요약 생성이 필요한 클러스터를 조회한다.
     */
    List<NewsCluster> findByStatusAndSummaryStatusIn(ClusterStatus status,
                                                     List<NewsCluster.SummaryStatus> statuses,
                                                     Pageable pageable);

    // --- 피드 목록: publishedAt 정렬 (기본) ---

    @Query("SELECT c FROM NewsCluster c " +
            "WHERE c.status = :status " +
            "AND c.summaryStatus IN :summaryStatuses " +
            "AND c.title IS NOT NULL " +
            "AND (c.publishedAt < :cursorTime " +
            "     OR (c.publishedAt = :cursorTime AND c.id < :cursorId)) " +
            "ORDER BY c.publishedAt DESC, c.id DESC")
    List<NewsCluster> findForFeedAfterCursorByPublishedAt(
            @Param("status") ClusterStatus status,
            @Param("summaryStatuses") List<SummaryStatus> summaryStatuses,
            @Param("cursorTime") OffsetDateTime cursorTime,
            @Param("cursorId") Long cursorId,
            Pageable pageable);

    @Query("SELECT c FROM NewsCluster c " +
            "WHERE c.status = :status " +
            "AND c.summaryStatus IN :summaryStatuses " +
            "AND c.title IS NOT NULL " +
            "ORDER BY c.publishedAt DESC, c.id DESC")
    List<NewsCluster> findForFeedFirstPageByPublishedAt(
            @Param("status") ClusterStatus status,
            @Param("summaryStatuses") List<SummaryStatus> summaryStatuses,
            Pageable pageable);

    /**
     * 금융 동향 생성용 — 최근 특정 시각 이후 발행된 클러스터를 최신순으로 조회한다
     *
     * {@code publishedAt}을 DB 조건으로 내려 nullable 상위 row로 인해 유효한 최근
     * 클러스터가 limit 밖으로 밀리는 문제를 방지한다
     */
    @Query("SELECT c FROM NewsCluster c " +
            "WHERE c.status = :status " +
            "AND c.summaryStatus IN :summaryStatuses " +
            "AND c.title IS NOT NULL " +
            "AND c.publishedAt IS NOT NULL " +
            "AND c.publishedAt >= :cutoff " +
            "ORDER BY c.publishedAt DESC, c.id DESC")
    List<NewsCluster> findRecentActiveClusters(
            @Param("status") ClusterStatus status,
            @Param("summaryStatuses") List<SummaryStatus> summaryStatuses,
            @Param("cutoff") OffsetDateTime cutoff,
            Pageable pageable);

    /**
     * 맞춤 금융 동향용 — 최근 특정 시각 이후 발행된 클러스터 중 사용자 관심사(STOCK/SECTOR/TOPIC) 와
     * 하나라도 매칭되는 클러스터를 최신순으로 조회한다.
     *
     * 매칭 조건은 {@code NewsClusterArticle} → {@code NewsArticleTag} EXISTS 서브쿼리로 표현하며,
     * 세 타입은 OR 결합. 빈 코드 리스트는 JPQL {@code IN ()} 에러를 내므로 호출 측에서 sentinel
     * (실제로 매칭되지 않을 빈 문자열 등)을 넣어 조건을 비활성화해야 한다
     */
    @Query("SELECT DISTINCT c FROM NewsCluster c " +
            "WHERE c.status = :status " +
            "AND c.summaryStatus IN :summaryStatuses " +
            "AND c.title IS NOT NULL " +
            "AND c.publishedAt IS NOT NULL " +
            "AND c.publishedAt >= :cutoff " +
            "AND EXISTS (" +
            "    SELECT 1 FROM NewsClusterArticle nca " +
            "    JOIN NewsArticleTag t ON t.newsArticleId = nca.newsArticleId " +
            "    WHERE nca.newsClusterId = c.id " +
            "    AND (" +
            "        (t.tagType = :stockType AND t.tagCode IN :stockCodes) " +
            "     OR (t.tagType = :sectorType AND t.tagCode IN :sectorCodes) " +
            "     OR (t.tagType = :topicType AND t.tagCode IN :topicCodes)" +
            "    )" +
            ") " +
            "ORDER BY c.publishedAt DESC, c.id DESC")
    List<NewsCluster> findPersonalizedClusters(
            @Param("status") ClusterStatus status,
            @Param("summaryStatuses") List<SummaryStatus> summaryStatuses,
            @Param("cutoff") OffsetDateTime cutoff,
            @Param("stockType") com.solv.wefin.domain.news.article.entity.NewsArticleTag.TagType stockType,
            @Param("stockCodes") List<String> stockCodes,
            @Param("sectorType") com.solv.wefin.domain.news.article.entity.NewsArticleTag.TagType sectorType,
            @Param("sectorCodes") List<String> sectorCodes,
            @Param("topicType") com.solv.wefin.domain.news.article.entity.NewsArticleTag.TagType topicType,
            @Param("topicCodes") List<String> topicCodes,
            Pageable pageable);

    // --- 피드 목록: updatedAt 정렬 ---

    @Query("SELECT c FROM NewsCluster c " +
            "WHERE c.status = :status " +
            "AND c.summaryStatus IN :summaryStatuses " +
            "AND c.title IS NOT NULL " +
            "AND (c.updatedAt < :cursorTime " +
            "     OR (c.updatedAt = :cursorTime AND c.id < :cursorId)) " +
            "ORDER BY c.updatedAt DESC, c.id DESC")
    List<NewsCluster> findForFeedAfterCursorByUpdatedAt(
            @Param("status") ClusterStatus status,
            @Param("summaryStatuses") List<SummaryStatus> summaryStatuses,
            @Param("cursorTime") OffsetDateTime cursorTime,
            @Param("cursorId") Long cursorId,
            Pageable pageable);

    @Query("SELECT c FROM NewsCluster c " +
            "WHERE c.status = :status " +
            "AND c.summaryStatus IN :summaryStatuses " +
            "AND c.title IS NOT NULL " +
            "ORDER BY c.updatedAt DESC, c.id DESC")
    List<NewsCluster> findForFeedFirstPageByUpdatedAt(
            @Param("status") ClusterStatus status,
            @Param("summaryStatuses") List<SummaryStatus> summaryStatuses,
            Pageable pageable);

    // --- 피드 목록: 태그 필터 (다중 tagCode IN) + publishedAt 정렬 ---

    @Query("SELECT DISTINCT c FROM NewsCluster c " +
            "WHERE c.status = :status " +
            "AND c.summaryStatus IN :summaryStatuses " +
            "AND c.title IS NOT NULL " +
            "AND EXISTS (SELECT 1 FROM NewsClusterArticle nca " +
            "            JOIN NewsArticleTag t ON t.newsArticleId = nca.newsArticleId " +
            "            WHERE nca.newsClusterId = c.id " +
            "            AND t.tagType = :tagType " +
            "            AND t.tagCode IN :tagCodes) " +
            "AND (c.publishedAt < :cursorTime " +
            "     OR (c.publishedAt = :cursorTime AND c.id < :cursorId)) " +
            "ORDER BY c.publishedAt DESC, c.id DESC")
    List<NewsCluster> findForFeedByTagsAfterCursorByPublishedAt(
            @Param("status") ClusterStatus status,
            @Param("summaryStatuses") List<SummaryStatus> summaryStatuses,
            @Param("tagType") com.solv.wefin.domain.news.article.entity.NewsArticleTag.TagType tagType,
            @Param("tagCodes") List<String> tagCodes,
            @Param("cursorTime") OffsetDateTime cursorTime,
            @Param("cursorId") Long cursorId,
            Pageable pageable);

    @Query("SELECT DISTINCT c FROM NewsCluster c " +
            "WHERE c.status = :status " +
            "AND c.summaryStatus IN :summaryStatuses " +
            "AND c.title IS NOT NULL " +
            "AND EXISTS (SELECT 1 FROM NewsClusterArticle nca " +
            "            JOIN NewsArticleTag t ON t.newsArticleId = nca.newsArticleId " +
            "            WHERE nca.newsClusterId = c.id " +
            "            AND t.tagType = :tagType " +
            "            AND t.tagCode IN :tagCodes) " +
            "ORDER BY c.publishedAt DESC, c.id DESC")
    List<NewsCluster> findForFeedByTagsFirstPageByPublishedAt(
            @Param("status") ClusterStatus status,
            @Param("summaryStatuses") List<SummaryStatus> summaryStatuses,
            @Param("tagType") com.solv.wefin.domain.news.article.entity.NewsArticleTag.TagType tagType,
            @Param("tagCodes") List<String> tagCodes,
            Pageable pageable);

    // --- 피드 목록: 태그 필터 (다중 tagCode IN) + updatedAt 정렬 ---

    @Query("SELECT DISTINCT c FROM NewsCluster c " +
            "WHERE c.status = :status " +
            "AND c.summaryStatus IN :summaryStatuses " +
            "AND c.title IS NOT NULL " +
            "AND EXISTS (SELECT 1 FROM NewsClusterArticle nca " +
            "            JOIN NewsArticleTag t ON t.newsArticleId = nca.newsArticleId " +
            "            WHERE nca.newsClusterId = c.id " +
            "            AND t.tagType = :tagType " +
            "            AND t.tagCode IN :tagCodes) " +
            "AND (c.updatedAt < :cursorTime " +
            "     OR (c.updatedAt = :cursorTime AND c.id < :cursorId)) " +
            "ORDER BY c.updatedAt DESC, c.id DESC")
    List<NewsCluster> findForFeedByTagsAfterCursorByUpdatedAt(
            @Param("status") ClusterStatus status,
            @Param("summaryStatuses") List<SummaryStatus> summaryStatuses,
            @Param("tagType") com.solv.wefin.domain.news.article.entity.NewsArticleTag.TagType tagType,
            @Param("tagCodes") List<String> tagCodes,
            @Param("cursorTime") OffsetDateTime cursorTime,
            @Param("cursorId") Long cursorId,
            Pageable pageable);

    @Query("SELECT DISTINCT c FROM NewsCluster c " +
            "WHERE c.status = :status " +
            "AND c.summaryStatus IN :summaryStatuses " +
            "AND c.title IS NOT NULL " +
            "AND EXISTS (SELECT 1 FROM NewsClusterArticle nca " +
            "            JOIN NewsArticleTag t ON t.newsArticleId = nca.newsArticleId " +
            "            WHERE nca.newsClusterId = c.id " +
            "            AND t.tagType = :tagType " +
            "            AND t.tagCode IN :tagCodes) " +
            "ORDER BY c.updatedAt DESC, c.id DESC")
    List<NewsCluster> findForFeedByTagsFirstPageByUpdatedAt(
            @Param("status") ClusterStatus status,
            @Param("summaryStatuses") List<SummaryStatus> summaryStatuses,
            @Param("tagType") com.solv.wefin.domain.news.article.entity.NewsArticleTag.TagType tagType,
            @Param("tagCodes") List<String> tagCodes,
            Pageable pageable);

    // --- 피드 목록: 조회수 정렬 (sort=view, Top N 고정, cursor 미지원) ---
    // publishedAt NULLS LAST: publishedAt == null 이면 정렬 상단에 끼지 않도록 명시.
    // cold start 시 동점(recent_view_count = 0) 에서 최신 우선 배치 의도 유지.

    @Query("SELECT c FROM NewsCluster c " +
            "WHERE c.status = :status " +
            "AND c.summaryStatus IN :summaryStatuses " +
            "AND c.title IS NOT NULL " +
            "ORDER BY c.recentViewCount DESC, c.publishedAt DESC NULLS LAST, c.id DESC")
    List<NewsCluster> findHotClusters(
            @Param("status") ClusterStatus status,
            @Param("summaryStatuses") List<SummaryStatus> summaryStatuses,
            Pageable pageable);

    @Query("SELECT DISTINCT c FROM NewsCluster c " +
            "WHERE c.status = :status " +
            "AND c.summaryStatus IN :summaryStatuses " +
            "AND c.title IS NOT NULL " +
            "AND EXISTS (SELECT 1 FROM NewsClusterArticle nca " +
            "            JOIN NewsArticleTag t ON t.newsArticleId = nca.newsArticleId " +
            "            WHERE nca.newsClusterId = c.id " +
            "            AND t.tagType = :tagType " +
            "            AND t.tagCode IN :tagCodes) " +
            "ORDER BY c.recentViewCount DESC, c.publishedAt DESC NULLS LAST, c.id DESC")
    List<NewsCluster> findHotClustersByTags(
            @Param("status") ClusterStatus status,
            @Param("summaryStatuses") List<SummaryStatus> summaryStatuses,
            @Param("tagType") com.solv.wefin.domain.news.article.entity.NewsArticleTag.TagType tagType,
            @Param("tagCodes") List<String> tagCodes,
            Pageable pageable);

    // --- 조회수 카운트 업데이트 (JPQL 원자 UPDATE로 Lost Update 방지) ---

    /**
     * ACTIVE 클러스터의 고유 뷰어 누적 카운트를 1 증가시킨다.
     *
     * {@code markRead} 에서 신규 INSERT가 성공한 경우에만 호출한다.
     *
     * {@code status = 'ACTIVE'} 가드는 {@code validateActiveCluster} 와 insert 사이 TOCTOU race 에서
     * 방금 INACTIVE 로 전환된 클러스터의 count 가 누적되지 않도록 방어한다. affected=0 이면 race 신호로 경고 로그.
     *
     * {@code clearAutomatically=true} 는 같은 트랜잭션에서 이후 JPA 로 NewsCluster 를 읽을 때
     * 네이티브/JPQL UPDATE 로 인한 L1 캐시 stale entity 를 피하기 위한 선방어.
     *
     * @param id 대상 클러스터 ID
     * @return 영향받은 row 수 — 정상이면 1, 0 이면 INACTIVE 전환 race 가능성으로 경고 로그 남겨야 함
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE NewsCluster c SET c.uniqueViewerCount = c.uniqueViewerCount + 1 " +
            "WHERE c.id = :id AND c.status = NewsCluster.ClusterStatus.ACTIVE")
    int incrementUniqueViewerCount(@Param("id") Long id);

    /**
     * 최근 N시간 윈도우 내 고유 뷰어 수를 ACTIVE 클러스터 전체에 대해 일괄 갱신한다.
     *
     * - 단일 UPDATE로 윈도우 스캔 1회만 수행 (2-step 분리보다 효율적)
     * - {@code IS DISTINCT FROM COALESCE(agg.cnt, 0)} 가드로 실제 값이 변한 row만 write — vacuum 부담 최소화
     * - 윈도우 밖으로 빠진 클러스터는 {@code agg.cnt IS NULL} → {@code COALESCE(0)} 로 자동 0 리셋
     *
     * @param windowStart 집계 대상 시각 하한(exclusive). 이 시각 이후의 read만 카운트됨
     * @return 실제로 값이 바뀌어 UPDATE된 row 수
     */
    @Modifying(clearAutomatically = true)
    @Query(value = """
            UPDATE news_cluster c
            SET recent_view_count = COALESCE(agg.cnt, 0)
            FROM news_cluster active
            LEFT JOIN (
                SELECT news_cluster_id AS id, COUNT(*) AS cnt
                FROM user_news_cluster_read
                WHERE read_at > :windowStart
                GROUP BY news_cluster_id
            ) agg ON agg.id = active.news_cluster_id
            WHERE c.news_cluster_id = active.news_cluster_id
              AND active.status = 'ACTIVE'
              AND c.recent_view_count IS DISTINCT FROM COALESCE(agg.cnt, 0)
            """, nativeQuery = true)
    int refreshRecentViewCounts(@Param("windowStart") OffsetDateTime windowStart);
}
