package com.solv.wefin.domain.user.repository;

import com.solv.wefin.domain.user.entity.UserInterest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface UserInterestRepository extends JpaRepository<UserInterest, Long> {

    Optional<UserInterest> findByUserIdAndInterestTypeAndInterestValue(
            UUID userId, String interestType, String interestValue);

    /**
     * 가중치를 원자적으로 증감한다. Lost Update 방지
     *
     * @return 업데이트된 행 수 (0이면 해당 관심사가 없음)
     */
    @Modifying
    @Query("UPDATE UserInterest u SET u.weight = u.weight + :delta " +
            "WHERE u.userId = :userId AND u.interestType = :type AND u.interestValue = :value")
    int addWeightAtomically(@Param("userId") UUID userId,
                            @Param("type") String type,
                            @Param("value") String value,
                            @Param("delta") BigDecimal delta);
}
