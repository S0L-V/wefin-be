package com.solv.wefin.domain.payment.repository;

import com.solv.wefin.domain.payment.entity.PaymentFailureLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentFailureLogRepository extends JpaRepository<PaymentFailureLog, Long> {
}