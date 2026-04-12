package com.solv.wefin.domain.trading.watchlist.repository;

import com.solv.wefin.domain.trading.watchlist.entity.InterestType;
import com.solv.wefin.domain.trading.watchlist.entity.UserInterest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UserInterestRepository extends JpaRepository<UserInterest, Long> {

    List<UserInterest> findByUserIdAndInterestType(UUID userId, InterestType interestType);

    boolean existsByUserIdAndInterestTypeAndInterestValue(UUID userId, InterestType interestType, String interestValue);

    long countByUserIdAndInterestType(UUID userId, InterestType interestType);

    void deleteByUserIdAndInterestValue(UUID userId, String interestValue);
}
