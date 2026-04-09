package com.solv.wefin.domain.news.cluster.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_news_cluster_feedback",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_news_cluster_feedback_user_cluster",
                columnNames = {"user_id", "news_cluster_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserNewsClusterFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_news_cluster_feedback_id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "news_cluster_id", nullable = false)
    private Long newsClusterId;

    @Enumerated(EnumType.STRING)
    @Column(name = "feedback_type", nullable = false, length = 20)
    private FeedbackType feedbackType;

    @Column(name = "submitted_at", nullable = false)
    private OffsetDateTime submittedAt;

    public enum FeedbackType {
        HELPFUL, NOT_HELPFUL
    }

    public static UserNewsClusterFeedback create(UUID userId, Long newsClusterId, FeedbackType feedbackType) {
        UserNewsClusterFeedback feedback = new UserNewsClusterFeedback();
        feedback.userId = userId;
        feedback.newsClusterId = newsClusterId;
        feedback.feedbackType = feedbackType;
        feedback.submittedAt = OffsetDateTime.now();
        return feedback;
    }
}
