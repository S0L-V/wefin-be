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
        left join fetch m.replyToMessage
        left join fetch m.replyToMessage.user
        left join fetch m.newsShare
        left join fetch m.newsShare.newsCluster
        where m.group.id = :groupId
        order by m.id desc
    """)
    List<ChatMessage> findMessagesByGroupId(@Param("groupId") Long groupId, Pageable pageable);

    @Query("""
        select m
        from ChatMessage m
        left join fetch m.user
        left join fetch m.group
        left join fetch m.replyToMessage
        left join fetch m.replyToMessage.user
        left join fetch m.newsShare
        left join fetch m.newsShare.newsCluster
        where m.group.id = :groupId
            and m.id < :beforeMessageId
        order by m.id desc
    """)
    List<ChatMessage> findMessagesByGroupIdBefore(
            @Param("groupId") Long groupId,
            @Param("beforeMessageId") Long beforeMessageId,
            Pageable pageable
    );

    long countByGroup_IdAndUser_UserIdAndCreatedAtAfter(Long groupId, UUID userId, OffsetDateTime time);

    Optional<ChatMessage> findByIdAndGroup_Id(Long messageId, Long groupId);

    @Query("""
        select count(m)
        from ChatMessage m
        where m.group.id = :groupId
          and m.id > :messageId
          and (m.user is null or m.user.userId <> :userId)
    """)
    long countUnreadAfterMessageId(@Param("groupId") Long groupId,
                                   @Param("messageId") Long messageId,
                                   @Param("userId") UUID userId);

    @Query("""
        select max(m.id)
        from ChatMessage m
        where m.group.id = :groupId
    """)
    Long findLatestMessageIdByGroupId(@Param("groupId") Long groupId);
}
