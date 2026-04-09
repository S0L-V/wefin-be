package com.solv.wefin.domain.news.cluster.repository;

import com.solv.wefin.domain.news.cluster.entity.UserNewsClusterFeedback;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserNewsClusterFeedbackRepository extends JpaRepository<UserNewsClusterFeedback, Long> {

    boolean existsByUserIdAndNewsClusterId(UUID userId, Long newsClusterId);

    Optional<UserNewsClusterFeedback> findByUserIdAndNewsClusterId(UUID userId, Long newsClusterId);
}
