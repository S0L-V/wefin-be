package com.solv.wefin.domain.chat.aiChat.repository;

import com.solv.wefin.domain.chat.aiChat.entity.AiChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AiChatMessageRepository extends JpaRepository<AiChatMessage, Long> {

    List<AiChatMessage> findByUser_UserIdOrderByMessageIdDesc(UUID userId, Pageable pageable);

    List<AiChatMessage> findByUser_UserIdAndMessageIdLessThanOrderByMessageIdDesc(
            UUID userId,
            Long beforeMessageId,
            Pageable pageable
    );

    List<AiChatMessage> findTop10ByUser_UserIdOrderByMessageIdDesc(UUID userId);
}
