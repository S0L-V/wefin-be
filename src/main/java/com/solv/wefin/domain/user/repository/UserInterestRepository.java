package com.solv.wefin.domain.user.repository;

import com.solv.wefin.domain.user.entity.UserInterest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserInterestRepository extends JpaRepository<UserInterest, Long> {

    Optional<UserInterest> findByUserIdAndInterestTypeAndInterestValue(
            UUID userId, String interestType, String interestValue);

    /**
     * 사용자가 명시적으로 등록한 관심사 목록만 조회한다.
     *
     * 피드백 기반 upsert row(manualRegistered=false)는 제외된다
     */
    List<UserInterest> findByUserIdAndInterestTypeAndManualRegisteredTrue(
            UUID userId, String interestType);

    /** 수동 등록 관심사의 존재 여부. Watchlist/Interest 중복 등록 방지에 사용 */
    boolean existsByUserIdAndInterestTypeAndInterestValueAndManualRegisteredTrue(
            UUID userId, String interestType, String interestValue);

    /** 수동 등록 관심사 개수. 타입별 10개 제한 계산에 사용 */
    long countByUserIdAndInterestTypeAndManualRegisteredTrue(UUID userId, String interestType);

    /** 수동 등록 관심사 row만 삭제한다. */
    void deleteByUserIdAndInterestTypeAndInterestValueAndManualRegisteredTrue(
            UUID userId, String interestType, String interestValue);

    /**
     * 추천 기반 관심사 weight를 upsert(없으면 생성, 있으면 누적)한다.
     *
     * - manual_registered는 INSERT에서 명시하지 않아 기본값(FALSE)을 사용한다.
     *   → 이 쿼리는 추천 기반 row(manual=false)만 대상으로 한다.
     *
     * - ON CONFLICT는 (user_id, interest_type, interest_value, manual_registered) 기준으로 동작한다.
     *   → manual=true(수동 등록 관심사) row와는 충돌하지 않으므로 절대 수정되지 않는다.
     *
     * - 동작 방식:
     *   1. 동일한 추천 row가 없으면 → 새로 생성
     *   2. 있으면 → 기존 weight에 delta를 더함
     *
     * - weight는 항상 0 ~ 30 범위로 제한된다.
     */
    @Modifying
    @Query(value = "INSERT INTO user_interest (user_id, interest_type, interest_value, weight, created_at) " +
            "VALUES (:userId, :type, :value, LEAST(GREATEST(:delta, 0), 30), NOW()) " +
            "ON CONFLICT (user_id, interest_type, interest_value, manual_registered) " +
            "DO UPDATE SET weight = LEAST( GREATEST( COALESCE(user_interest.weight, 0) + :delta, 0 ), 30 )",
            nativeQuery = true)
    void upsertWeight(@Param("userId") UUID userId,
                      @Param("type") String type,
                      @Param("value") String value,
                      @Param("delta") BigDecimal delta);
}
