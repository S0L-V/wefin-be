package com.solv.wefin.domain.quest.entity;

import com.solv.wefin.global.common.BaseEntity;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "quest_template")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuestTemplate extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "template_id")
    private Long id;

    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "title", nullable = false, length = 100)
    private String title;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "complete_type", nullable = false, length = 30)
    private QuestCompleteType completeType;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private QuestEventType eventType;

    @Column(name = "target_value", nullable = false)
    private Integer targetValue;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "condition_json", columnDefinition = "jsonb")
    private String conditionJson;

    @Column(name = "reward", nullable = false)
    private Integer reward;

    @Column(name = "is_repeatable", nullable = false)
    private Boolean repeatable;

    @Column(name = "is_active", nullable = false)
    private Boolean active;

    @PrePersist
    protected void onCreate() {
        validateInvariant();

        if (this.repeatable == null) {
            this.repeatable = false;
        }
        if (this.active == null) {
            this.active = true;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        validateInvariant();
    }

    private void validateInvariant() {
        if (this.targetValue == null || this.targetValue <= 0) {
            throw new BusinessException(ErrorCode.QUEST_TARGET_VALUE_INVALID);
        }

        if (this.reward == null || this.reward < 0) {
            throw new BusinessException(ErrorCode.QUEST_REWARD_INVALID);
        }
    }

    public void deactivate() {
        this.active = false;
    }

    public void activate() {
        this.active = true;
    }
}
