package com.solv.wefin.domain.chat.globalChat.entity;

import com.solv.wefin.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

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
    private Users user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ChatRole role;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Builder
    public GlobalChatMessage(Users user, ChatRole role, String content, LocalDateTime createdAt) {
        this.user = user;
        this.role = role;
        this.content = content;
        this.createdAt = createdAt;
    }
}