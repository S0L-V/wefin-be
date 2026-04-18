package com.solv.wefin.domain.news.cluster.repository;

import com.solv.wefin.domain.news.cluster.entity.UserNewsClusterRead;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UserNewsClusterReadRepository extends JpaRepository<UserNewsClusterRead, Long> {

    boolean existsByUserIdAndNewsClusterId(UUID userId, Long newsClusterId);

    List<UserNewsClusterRead> findByUserIdAndNewsClusterIdIn(UUID userId, List<Long> newsClusterIds);
}
