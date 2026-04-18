package com.solv.wefin.domain.news.cluster.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_news_cluster_read",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_news_cluster_read_user_cluster",
                columnNames = {"user_id", "news_cluster_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserNewsClusterRead {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_news_cluster_read_id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "news_cluster_id", nullable = false)
    private Long newsClusterId;

    @Column(name = "read_at", nullable = false)
    private OffsetDateTime readAt;

    public static UserNewsClusterRead create(UUID userId, Long newsClusterId) {
        UserNewsClusterRead read = new UserNewsClusterRead();
        read.userId = userId;
        read.newsClusterId = newsClusterId;
        read.readAt = OffsetDateTime.now();
        return read;
    }
}
