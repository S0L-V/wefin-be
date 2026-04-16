package com.solv.wefin.domain.auth.repository;

import com.solv.wefin.domain.auth.entity.EmailVerification;
import com.solv.wefin.domain.auth.entity.VerificationPurpose;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmailVerificationRepository extends JpaRepository<EmailVerification, Long> {

    Optional<EmailVerification> findByEmailAndPurpose(String email, VerificationPurpose purpose);
}