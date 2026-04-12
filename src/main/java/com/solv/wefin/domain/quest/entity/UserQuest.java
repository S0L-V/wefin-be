package com.solv.wefin.domain.quest.entity;

import com.solv.wefin.domain.auth.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Entity
@Table(
        name = "user_quest",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_user_quest_user_daily_quest",
                columnNames = {"user_id", "daily_quest_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserQuest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "quest_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "daily_quest_id", nullable = false)
    private DailyQuest dailyQuest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private QuestStatus status;

    @Column(name = "progress", nullable = false)
    private Integer progress;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Builder
    private UserQuest (
            DailyQuest dailyQuest,
            User user,
            QuestStatus status,
            Integer progress
    ) {
        this.dailyQuest = dailyQuest;
        this.user = user;
        this.status = status;
        this.progress = progress;
    }

    public static UserQuest assign(User user, DailyQuest dailyQuest) {
        return UserQuest.builder()
                .user(user)
                .dailyQuest(dailyQuest)
                .status(QuestStatus.NOT_STARTED)
                .progress(0)
                .build();
    }

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();

        if (this.status == null) {
            this.status = QuestStatus.NOT_STARTED;
        }
        if (this.progress == null) {
            this.progress = 0;
        }

        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }

    public void start() {
        if (this.status == QuestStatus.NOT_STARTED) {
            this.status = QuestStatus.IN_PROGRESS;
            this.startedAt = OffsetDateTime.now();
        }
    }

    public void updateProgress(int progress) {
        this.progress = progress;

        if (progress > 0 && this.status == QuestStatus.NOT_STARTED) {
            this.status = QuestStatus.IN_PROGRESS;
            this.startedAt = OffsetDateTime.now();
        }

        Integer targetValue = this.dailyQuest.getTargetValue();
        if (targetValue != null && progress >= targetValue) {
            complete();
        }
    }

    public void complete() {
        this.status = QuestStatus.COMPLETED;
        this.completedAt = OffsetDateTime.now();

        if (this.startedAt == null) {
            this.startedAt = this.completedAt;
        }
    }

    public void markRewarded() {
        this.status = QuestStatus.REWARDED;
    }
}

