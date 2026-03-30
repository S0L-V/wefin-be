package com.solv.wefin.domain.chat.globalChat.entity;

import com.solv.wefin.domain.auth.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Entity
@Table(name = "global_chat_message")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GlobalChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "message_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ChatRole role;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Builder
    public GlobalChatMessage(User user, ChatRole role, String content, OffsetDateTime createdAt) {
        this.user = user;
        this.role = role;
        this.content = content;
        this.createdAt = createdAt;
    }

    public static GlobalChatMessage createUserMessage(User user, String content) {
        return GlobalChatMessage.builder()
                .user(user)
                .role(ChatRole.USER)
                .content(content)
                .createdAt(OffsetDateTime.now())
                .build();
    }

    public static GlobalChatMessage createSystemMessage(String content) {
        return GlobalChatMessage.builder()
                .user(null)
                .role(ChatRole.SYSTEM)
                .content(content)
                .createdAt(OffsetDateTime.now())
                .build();
    }
}