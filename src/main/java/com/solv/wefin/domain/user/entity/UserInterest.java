package com.solv.wefin.domain.user.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 사용자 관심사 (분야/종목/주제)
 *
 * 뉴스 피드백 등의 행동으로 가중치가 누적되며, 개인화 추천에 활용된다
 */
@Entity
@Table(name = "user_interest",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_interest_user_type_value",
                columnNames = {"user_id", "interest_type", "interest_value"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserInterest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_interest_id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "interest_type", nullable = false, length = 30)
    private String interestType;

    @Column(name = "interest_value", nullable = false, length = 100)
    private String interestValue;

    @Column(name = "weight", precision = 5, scale = 2)
    private BigDecimal weight;

    @Column(name = "manual_registered", nullable = false)
    private boolean manualRegistered; //사용자가 명시적으로 등록한 관심사 여부

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = OffsetDateTime.now();
    }

    /**
     * 피드백 등에 의해 가중치를 증감한다
     */
    public void addWeight(BigDecimal delta) {
        this.weight = (this.weight != null ? this.weight : BigDecimal.ZERO).add(delta);
    }

    /**
     * 사용자가 명시적으로 등록한 관심사를 생성한다.
     *
     * Watchlist(STOCK) 및 Interest API(SECTOR/TOPIC)의 저장 경로에서 사용한다
     */
    public static UserInterest createManual(UUID userId, String interestType, String interestValue, BigDecimal weight) {
        UserInterest interest = new UserInterest();
        interest.userId = userId;
        interest.interestType = interestType;
        interest.interestValue = interestValue;
        interest.weight = weight;
        interest.manualRegistered = true;
        return interest;
    }
}
