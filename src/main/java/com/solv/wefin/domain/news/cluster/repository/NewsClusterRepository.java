package com.solv.wefin.domain.news.cluster.repository;

import com.solv.wefin.domain.news.cluster.entity.NewsCluster;
import com.solv.wefin.domain.news.cluster.entity.NewsCluster.ClusterStatus;
import com.solv.wefin.domain.news.cluster.entity.NewsCluster.SummaryStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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
}
