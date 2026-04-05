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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reply_to_message_id")
    private ChatMessage replyToMessage;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Builder
    public ChatMessage(User user, Group group, MessageType messageType, String content, ChatMessage replyToMessage, OffsetDateTime createdAt) {
        this.user = user;
        this.group = group;
        this.messageType = messageType;
        this.content = content;
        this.replyToMessage = replyToMessage;
        this.createdAt = createdAt;
    }

    public static ChatMessage createUserMessage(User user, Group group, String content, ChatMessage replyToMessage) {
        return ChatMessage.builder()
                .user(user)
                .group(group)
                .messageType(MessageType.CHAT)
                .content(content)
                .replyToMessage(replyToMessage)
                .createdAt(OffsetDateTime.now())
                .build();
    }
}

