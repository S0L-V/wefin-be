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
    List<GlobalChatMessage> findMessages(Pageable pageable);

    @Query("""
        select m
        from GlobalChatMessage m
        left join fetch m.user
        where m.id < :beforeMessageId
        order by m.id desc 
    """)
    List<GlobalChatMessage> findMessagesBefore(Long beforeMessageId, Pageable pageable);

    long countByUser_UserIdAndCreatedAtAfter(UUID userId, OffsetDateTime time);

    @Query("""
        select count(m)
        from GlobalChatMessage m
        where m.id > :messageId
          and (m.user is null or m.user.userId <> :userId)
    """)
    long countUnreadAfterMessageId(@org.springframework.data.repository.query.Param("messageId") Long messageId,
                                   @org.springframework.data.repository.query.Param("userId") UUID userId);

    @Query("""
        select max(m.id)
        from GlobalChatMessage m
    """)
    Long findLatestMessageId();
}
