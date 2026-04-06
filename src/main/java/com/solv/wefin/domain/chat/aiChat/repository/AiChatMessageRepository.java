package com.solv.wefin.domain.chat.aiChat.repository;

import com.solv.wefin.domain.auth.entity.User;
import com.solv.wefin.domain.chat.aiChat.entity.AiChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AiChatMessageRepository extends JpaRepository<AiChatMessage, Long> {

    List<AiChatMessage> findByUser_UserIdOrderByCreatedAtAsc(UUID userId);

    List<AiChatMessage> findTop10ByUser_UserIdOrderByCreatedAtDesc(UUID userId);
}
