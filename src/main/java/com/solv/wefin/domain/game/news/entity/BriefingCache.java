package com.solv.wefin.domain.game.news.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "briefing_cache")
public class BriefingCache {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "briefing_id")
    private UUID briefingId;

    @Column(name = "target_date", nullable = false, unique = true)
    private LocalDate targetDate;

    @Column(name = "briefing_text", nullable = false, columnDefinition = "TEXT")
    private String briefingText;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public static BriefingCache create(LocalDate targetDate, String briefingText) {
        BriefingCache cache = new BriefingCache();
        cache.targetDate = targetDate;
        cache.briefingText = briefingText;
        cache.createdAt = OffsetDateTime.now();
        return cache;
    }
}

