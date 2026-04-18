package com.solv.wefin.domain.payment.repository;

import com.solv.wefin.domain.payment.entity.Subscription;
import com.solv.wefin.domain.payment.entity.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    boolean existsByUserUserIdAndStatus(UUID userId, SubscriptionStatus status);

    Optional<Subscription> findByUserUserIdAndStatus(UUID userId, SubscriptionStatus status);
}
