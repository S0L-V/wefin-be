package com.solv.wefin.domain.news.recommendation.repository;

import com.solv.wefin.domain.news.recommendation.entity.RecommendedNewsCard;
import com.solv.wefin.domain.news.recommendation.entity.RecommendedNewsCard.CardType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 추천 뉴스 카드 저장소
 */
public interface RecommendedNewsCardRepository extends JpaRepository<RecommendedNewsCard, Long> {

    /**
     * 해당 사용자의 모든 카드를 최신순으로 반환한다 (세션 유효성·hash 비교 등)
     */
    List<RecommendedNewsCard> findByUserIdOrderByCreatedAtDesc(UUID userId);

    /**
     * 해당 사용자·카드 타입에서 이미 사용된 관심사 코드 목록을 반환한다 (중복 방지)
     */
    @Query("SELECT DISTINCT c.interestCode FROM RecommendedNewsCard c " +
            "WHERE c.userId = :userId AND c.cardType = :cardType")
    List<String> findUsedInterestCodes(@Param("userId") UUID userId,
                                       @Param("cardType") CardType cardType);

    /**
     * 세션 만료된 카드를 일괄 삭제한다
     */
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM RecommendedNewsCard c WHERE c.userId = :userId " +
            "AND c.sessionStartedAt < :cutoff")
    int deleteExpiredCards(@Param("userId") UUID userId,
                          @Param("cutoff") OffsetDateTime cutoff);

    /**
     * 해당 사용자의 모든 카드를 삭제한다 (관심사 변경 시 이력 리셋)
     */
    @Modifying
    void deleteByUserId(UUID userId);
}
