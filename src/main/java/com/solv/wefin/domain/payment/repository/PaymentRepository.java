package com.solv.wefin.domain.payment.repository;

import com.solv.wefin.domain.payment.entity.Payment;
import com.solv.wefin.domain.payment.entity.PaymentProvider;
import com.solv.wefin.domain.payment.entity.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    boolean existsByOrderId(String orderId);

    Optional<Payment> findByOrderId(String orderId);

    Optional<Payment> findByOrderIdAndUserUserId(String orderId, UUID userId);

    // 같은 사용자 / 같은 플랜의 기존 READY결제 재사용 여부 확인
    Optional<Payment> findTopByUserUserIdAndPlanPlanIdAndProviderAndStatusOrderByRequestedAtDesc(
            UUID userId,
            Long planId,
            PaymentProvider provider,
            PaymentStatus status
    );
}