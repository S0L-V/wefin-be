package com.solv.wefin.domain.news.article.repository;

import com.solv.wefin.domain.news.article.entity.NewsArticleTag;
import com.solv.wefin.domain.news.cluster.entity.NewsCluster.ClusterStatus;
import com.solv.wefin.domain.news.cluster.entity.NewsCluster.SummaryStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NewsArticleTagRepository extends JpaRepository<NewsArticleTag, Long> {

    /**
     * 피드에 노출 가능한 클러스터에 속한 기사의 인기 태그를 조회한다.
     * 클러스터 수 기준 내림차순 정렬. Pageable로 limit 제어
     */
    /**
     * tagName이 아닌 tagCode만으로 그룹핑한다.
     * SECTOR/TOPIC은 마스터 테이블이 없어 AI가 같은 코드에 다른 이름("기술"/"테크")을 붙이면
     * (tagCode, tagName) 그룹이 쪼개져 집계가 왜곡된다. tagCode를 SoT로 삼고,
     * 표시명은 MIN(tagName)으로 결정적으로 고정하여 쪼개짐을 원천 차단한다
     */
    @Query("SELECT t.tagCode AS tagCode, MIN(t.tagName) AS tagName, COUNT(DISTINCT nc.id) AS clusterCount " +
            "FROM NewsArticleTag t " +
            "JOIN NewsClusterArticle nca ON nca.newsArticleId = t.newsArticleId " +
            "JOIN NewsCluster nc ON nc.id = nca.newsClusterId " +
            "WHERE t.tagType = :tagType " +
            "AND nc.status = :status " +
            "AND nc.summaryStatus IN :summaryStatuses " +
            "AND nc.title IS NOT NULL " +
            "GROUP BY t.tagCode " +
            "ORDER BY COUNT(DISTINCT nc.id) DESC")
    List<PopularTagProjection> findPopularTags(
            @Param("tagType") NewsArticleTag.TagType tagType,
            @Param("status") ClusterStatus status,
            @Param("summaryStatuses") List<SummaryStatus> summaryStatuses,
            Pageable pageable);

    interface PopularTagProjection {
        String getTagCode();
        String getTagName();
        Long getClusterCount();
    }

    List<NewsArticleTag> findByNewsArticleId(Long newsArticleId);

    /**
     * 여러 기사의 태그를 한 번에 조회한다.
     */
    List<NewsArticleTag> findByNewsArticleIdIn(List<Long> newsArticleIds);

    /**
     * 여러 기사의 특정 타입 태그만 조회한다.
     */
    List<NewsArticleTag> findByNewsArticleIdInAndTagType(List<Long> newsArticleIds, NewsArticleTag.TagType tagType);

    /**
     * 특정 (tagType, tagCode) 조합이 하나라도 존재하는지 확인한다.
     *
     * 관심사 SECTOR/TOPIC 등록 시 자유 입력 대신 실제 부여한 태그만 허용하기 위한 유효성 검증에 사용한다
     */
    boolean existsByTagTypeAndTagCode(NewsArticleTag.TagType tagType, String tagCode);

    /**
     * 특정 (tagType, tagCode)의 표시명(tagName)을 가장 최근 기사 기준으로 조회한다.
     *
     * AI가 동일 code에 여러 표기를 시간에 따라 부여한 경우가 있어, 사전순(MIN) 대신
     * 최신 삽입된 tagName을 선택해 "최근 운영에서 사용 중인 명칭"을 보여준다.
     * 여러 건을 batch로 조회하는 {@link #findTagNamesByTagTypeAndTagCodes}를 우선 사용하라
     */
    @Query(value = "SELECT tag_name FROM news_article_tag " +
            "WHERE tag_type = :tagType AND tag_code = :tagCode " +
            "ORDER BY news_article_tag_id DESC LIMIT 1", nativeQuery = true)
    String findTagNameByTagTypeAndTagCode(@Param("tagType") String tagType,
                                          @Param("tagCode") String tagCode);

    /**
     * 여러 tagCode의 표시명을 한 번에 조회한다 (N+1 방지).
     *
     * 같은 code에 여러 tagName이 존재할 경우 {@link #findTagNameByTagTypeAndTagCode}와
     * 동일하게 최신 삽입된 값을 선택한다
     */
    @Query(value = "SELECT DISTINCT ON (tag_code) tag_code AS code, tag_name AS name " +
            "FROM news_article_tag " +
            "WHERE tag_type = :tagType AND tag_code IN (:tagCodes) " +
            "ORDER BY tag_code, news_article_tag_id DESC", nativeQuery = true)
    List<TagNameProjection> findTagNamesByTagTypeAndTagCodes(
            @Param("tagType") String tagType,
            @Param("tagCodes") java.util.Collection<String> tagCodes);

    interface TagNameProjection {
        String getCode();
        String getName();
    }

    /**
     * 특정 기사의 태그를 전부 삭제한다. 재태깅 시 기존 태그 정리에 사용한다.
     */
    void deleteByNewsArticleId(Long newsArticleId);
}
