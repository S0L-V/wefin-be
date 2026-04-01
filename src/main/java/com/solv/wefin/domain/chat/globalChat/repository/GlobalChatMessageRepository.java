package com.solv.wefin.domain.chat.globalChat.repository;

import com.solv.wefin.domain.chat.globalChat.entity.GlobalChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface GlobalChatMessageRepository extends JpaRepository<GlobalChatMessage, Long> {

    @Query("""
        select m
        from GlobalChatMessage m
        left join fetch m.user
        order by m.id desc
    """)
    List<GlobalChatMessage> findRecentMessages(Pageable pageable);

    long countByUser_UserIdAndCreatedAtAfter(UUID userId, OffsetDateTime time);
}
