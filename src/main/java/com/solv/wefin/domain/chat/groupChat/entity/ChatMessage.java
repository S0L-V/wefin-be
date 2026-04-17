package com.solv.wefin.domain.chat.groupChat.entity;

import com.solv.wefin.domain.auth.entity.User;
import com.solv.wefin.domain.group.entity.Group;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Entity
@Table(name = "chat_message")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "message_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 10)
    private MessageType messageType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "ref_type", length = 20)
    private RefType refType;

    @Column(name = "ref_id")
    private Long refId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reply_to_message_id")
    private ChatMessage replyToMessage;

    @OneToOne(mappedBy = "chatMessage", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private ChatMessageNewsShare newsShare;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Builder
    public ChatMessage(User user, Group group, MessageType messageType, String content, RefType refType, Long refId, ChatMessage replyToMessage, OffsetDateTime createdAt) {
        this.user = user;
        this.group = group;
        this.messageType = messageType;
        this.content = content;
        this.refType = refType;
        this.refId = refId;
        this.replyToMessage = replyToMessage;
        this.createdAt = createdAt;
    }

    public static ChatMessage createUserMessage(User user, Group group, String content, ChatMessage replyToMessage) {
        return ChatMessage.builder()
                .user(user)
                .group(group)
                .messageType(MessageType.CHAT)
                .content(content)
                .refType(null)
                .refId(null)
                .replyToMessage(replyToMessage)
                .createdAt(OffsetDateTime.now())
                .build();
    }

    public static ChatMessage createNewsMessage(User user, Group group, String title) {
        return ChatMessage.builder()
                .user(user)
                .group(group)
                .messageType(MessageType.NEWS)
                .content(title)
                .refType(null)
                .refId(null)
                .replyToMessage(null)
                .createdAt(OffsetDateTime.now())
                .build();
    }

    public static ChatMessage createVoteMessage(User user, Group group, String content, Long voteId) {
        return ChatMessage.builder()
                .user(user)
                .group(group)
                .messageType(MessageType.CHAT)
                .content(content)
                .refType(RefType.VOTE)
                .refId(voteId)
                .replyToMessage(null)
                .createdAt(OffsetDateTime.now())
                .build();
    }

    public void attachNewsShare(ChatMessageNewsShare newsShare) {
        this.newsShare = newsShare;
    }
}

