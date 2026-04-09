package com.solv.wefin.domain.chat.groupChat.entity;

import com.solv.wefin.domain.news.cluster.entity.NewsCluster;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "chat_message_news_share")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessageNewsShare {

    @Id
    @Column(name = "chat_message_id")
    private Long chatMessageId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_message_id", nullable = false)
    private ChatMessage chatMessage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "news_cluster_id", nullable = false)
    private NewsCluster newsCluster;

    @Column(name = "shared_title", nullable = false)
    private String sharedTitle;

    @Column(name = "shared_summary", columnDefinition = "TEXT")
    private String sharedSummary;

    @Column(name = "shared_thumbnail_url", columnDefinition = "TEXT")
    private String sharedThumbnailUrl;

    @Builder
    private ChatMessageNewsShare(
            ChatMessage chatMessage,
            NewsCluster newsCluster,
            String sharedTitle,
            String sharedSummary,
            String sharedThumbnailUrl
    ) {
        this.chatMessage = chatMessage;
        this.newsCluster = newsCluster;
        this.sharedTitle = sharedTitle;
        this.sharedSummary = sharedSummary;
        this.sharedThumbnailUrl = sharedThumbnailUrl;
    }

    public static ChatMessageNewsShare create(
            ChatMessage chatMessage,
            NewsCluster newsCluster
    ) {
        return ChatMessageNewsShare.builder()
                .chatMessage(chatMessage)
                .newsCluster(newsCluster)
                .sharedTitle(newsCluster.getTitle())
                .sharedSummary(newsCluster.getSummary())
                .sharedThumbnailUrl(newsCluster.getThumbnailUrl())
                .build();
    }
}
