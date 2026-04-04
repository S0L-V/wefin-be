package com.solv.wefin.domain.chat.groupChat.repository;

import com.solv.wefin.domain.chat.groupChat.entity.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    @Query("""
        select m
        from ChatMessage m
        left join fetch m.user
        left join fetch m.group
        where m.group.id = :groupId
        order by m.id desc
    """)
    List<ChatMessage> findRecentMessagesByGroupId(@Param("groupId") Long groupId, Pageable pageable);

    long countByGroup_IdAndUser_UserIdAndCreatedAtAfter(Long groupId, UUID userId, OffsetDateTime time);

    Optional<ChatMessage> findByIdAndGroup_Id(Long messageId, Long groupId);
}
