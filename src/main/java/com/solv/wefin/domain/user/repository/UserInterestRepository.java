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

    List<UserInterest> findByUserIdAndInterestType(UUID userId, String interestType);

    boolean existsByUserIdAndInterestTypeAndInterestValue(UUID userId, String interestType, String interestValue);

    long countByUserIdAndInterestType(UUID userId, String interestType);

    void deleteByUserIdAndInterestTypeAndInterestValue(UUID userId, String interestType, String interestValue);

    /**
     * 가중치를 원자적으로 upsert한다.
     * 존재하면 weight를 증감하고, 없으면 새로 생성한다.
     * PostgreSQL INSERT ON CONFLICT DO UPDATE로 단일 쿼리 실행
     */
    @Modifying
    @Query(value = "INSERT INTO user_interest (user_id, interest_type, interest_value, weight, created_at) " +
            "VALUES (:userId, :type, :value, LEAST(GREATEST(:delta, 0), 30), NOW()) " +
            "ON CONFLICT (user_id, interest_type, interest_value) " +
            "DO UPDATE SET weight = LEAST( GREATEST( COALESCE(user_interest.weight, 0) + :delta, 0 ), 30 )",
            nativeQuery = true)
    void upsertWeight(@Param("userId") UUID userId,
                      @Param("type") String type,
                      @Param("value") String value,
                      @Param("delta") BigDecimal delta);
}
