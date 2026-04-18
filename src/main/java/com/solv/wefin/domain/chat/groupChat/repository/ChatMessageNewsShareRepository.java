package com.solv.wefin.domain.chat.groupChat.repository;

import com.solv.wefin.domain.chat.groupChat.entity.ChatMessageNewsShare;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChatMessageNewsShareRepository  extends JpaRepository<ChatMessageNewsShare, Long> {
    Optional<ChatMessageNewsShare> findByChatMessage_Id(Long messageId);
}
