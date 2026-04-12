package com.solv.wefin.domain.quest.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(
        name = "daily_quest",
        uniqueConstraints  = @UniqueConstraint(
                name = "uq_daily_quest_date_template",
                columnNames = {"quest_date", "template_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailyQuest {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        @Column(name = "daily_quest_id")
        private Long id;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "template_id", nullable = false)
        private QuestTemplate questTemplate;

        @Column(name = "quest_date", nullable = false)
        private LocalDate questDate;

        @Column(name = "target_value", nullable = false)
        private Integer targetValue;

        @Column(name = "reward", nullable = false)
        private Integer reward;

        @Column(name = "created_at", nullable = false)
        private OffsetDateTime createdAt;

        @Column(name = "updated_at", nullable = false)
        private OffsetDateTime updatedAt;

        @Builder
        private DailyQuest(
                QuestTemplate questTemplate,
                LocalDate questDate,
                Integer targetValue,
                Integer reward
        ) {
                this.questTemplate = questTemplate;
                this.questDate = questDate;
                this.targetValue = targetValue;
                this.reward = reward;
        }

        public static DailyQuest create(
                QuestTemplate questTemplate,
                LocalDate questDate,
                Integer targetValue,
                Integer reward
        ) {
                return DailyQuest.builder()
                        .questTemplate(questTemplate)
                        .questDate(questDate)
                        .targetValue(targetValue)
                        .reward(reward)
                        .build();
        }

        @PrePersist
        protected void onCreate() {
                OffsetDateTime now = OffsetDateTime.now();
                this.createdAt = now;
                this.updatedAt = now;
        }

        @PreUpdate
        protected void onUpdate() {
                this.updatedAt = OffsetDateTime.now();
        }
}
