package com.solv.wefin.domain.auth.repository;

import com.solv.wefin.domain.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

// 추후 다중 토큰 관리 가능성을 고려해 유지
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByToken(String token);
}
