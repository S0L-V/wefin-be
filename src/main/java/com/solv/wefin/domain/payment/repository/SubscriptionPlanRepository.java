package com.solv.wefin.domain.payment.repository;

import com.solv.wefin.domain.payment.entity.SubscriptionPlan;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, Long> {
}
