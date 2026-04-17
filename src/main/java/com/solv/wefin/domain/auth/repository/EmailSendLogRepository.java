package com.solv.wefin.domain.auth.repository;

import com.solv.wefin.domain.auth.entity.EmailSendLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailSendLogRepository extends JpaRepository<EmailSendLog, Long> {
}