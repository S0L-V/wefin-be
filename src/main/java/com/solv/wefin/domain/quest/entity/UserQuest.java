package com.solv.wefin.domain.quest.entity;

import com.solv.wefin.domain.auth.entity.User;
import com.solv.wefin.global.common.BaseEntity;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
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
public class UserQuest extends BaseEntity {

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
    }

    public void start() {
        if (this.status == QuestStatus.NOT_STARTED) {
            this.status = QuestStatus.IN_PROGRESS;
            this.startedAt = OffsetDateTime.now();
        }
    }

    public void updateProgress(int progress) {

        if (progress < 0) {
            throw new BusinessException(ErrorCode.QUEST_PROGRESS_INVALID);
        }

        if (this.status == QuestStatus.COMPLETED || this.status == QuestStatus.REWARDED) {
            return;
        }

        Integer targetValue = this.dailyQuest.getTargetValue();
        this.progress = targetValue != null ? Math.min(progress, targetValue) : progress;

        if (progress > 0 && this.status == QuestStatus.NOT_STARTED) {
            this.status = QuestStatus.IN_PROGRESS;
            this.startedAt = OffsetDateTime.now();
        }

        if (targetValue != null && progress >= targetValue) {
            complete();
        }
    }

    private void complete() {

        if (this.status == QuestStatus.COMPLETED || this.status == QuestStatus.REWARDED) {
            return;
        }
        this.status = QuestStatus.COMPLETED;
        this.completedAt = OffsetDateTime.now();

        if (this.startedAt == null) {
            this.startedAt = this.completedAt;
        }
    }

    public void markRewarded() {

        if (this.status != QuestStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.QUEST_REWARD_NOT_ALLOWED);
        }

        this.status = QuestStatus.REWARDED;
    }
}

