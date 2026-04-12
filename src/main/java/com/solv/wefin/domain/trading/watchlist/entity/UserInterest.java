package com.solv.wefin.domain.trading.watchlist.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_interest")
@Getter
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserInterest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userInterestId;

    @Column(nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "interest_type", nullable = false, length = 30)
    private InterestType interestType;

    @Column(name = "interest_value", nullable = false, length = 100)
    private String interestValue;

    private BigDecimal weight;

    @CreatedDate
    private OffsetDateTime createdAt;

    public UserInterest(UUID userId, InterestType interestType, String interestValue) {
        this.userId = userId;
        this.interestType = interestType;
        this.interestValue = interestValue;
    }

}
