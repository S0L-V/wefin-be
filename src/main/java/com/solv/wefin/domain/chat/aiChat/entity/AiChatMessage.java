package com.solv.wefin.domain.chat.aiChat.entity;

import com.solv.wefin.domain.auth.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Entity
@Table(name = "ai_chat_message")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "message_id")
    private Long messageId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AiChatRole role;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Builder
    public AiChatMessage(User user, AiChatRole role, String content, OffsetDateTime createdAt) {
        this.user = user;
        this.role = role;
        this.content = content;
        this.createdAt = createdAt;
    }

    public static AiChatMessage createUserMessage(User user, String content) {
        return AiChatMessage.builder()
                .user(user)
                .role(AiChatRole.USER)
                .content(content)
                .createdAt(OffsetDateTime.now())
                .build();
    }

    public static AiChatMessage createAiMessage(User user, String content) {
        return AiChatMessage.builder()
                .user(user)
                .role(AiChatRole.AI)
                .content(content)
                .createdAt(OffsetDateTime.now())
                .build();
    }

    public enum AiChatRole {
        USER, AI
    }
}

