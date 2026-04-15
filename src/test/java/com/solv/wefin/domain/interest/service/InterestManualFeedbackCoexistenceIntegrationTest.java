package com.solv.wefin.domain.interest.service;

import com.solv.wefin.domain.trading.watchlist.entity.InterestType;
import com.solv.wefin.domain.user.entity.UserInterest;
import com.solv.wefin.domain.user.repository.UserInterestRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 수동 등록 row와 피드백 upsert row가 같은 (user, type, code)에 공존 가능한지,
 * 재등록 흐름(manual row 삭제 → 피드백 upsert → 재등록)에서 unique 충돌이 없는지 DB로 검증한다.
 */
@SpringBootTest
@ActiveProfiles("test")
class InterestManualFeedbackCoexistenceIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(
                    DockerImageName.parse("pgvector/pgvector:pg16")
                            .asCompatibleSubstituteFor("postgres"))
                    .withDatabaseName("wefin_test")
                    .withUsername("test")
                    .withPassword("test");

    static {
        postgres.start();
    }

    @Autowired private UserInterestRepository userInterestRepository;
    @Autowired private TransactionTemplate transactionTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    private final UUID userId = UUID.randomUUID();

    @AfterEach
    void cleanup() {
        transactionTemplate.executeWithoutResult(tx -> {
            entityManager.createNativeQuery("DELETE FROM user_interest WHERE user_id = ?")
                    .setParameter(1, userId).executeUpdate();
            entityManager.createNativeQuery("DELETE FROM users WHERE user_id = ?")
                    .setParameter(1, userId).executeUpdate();
        });
    }

    @Test
    @DisplayName("같은 (user,type,code)에 manual=TRUE와 manual=FALSE가 동시에 존재할 수 있다")
    void manualAndFeedback_canCoexist() {
        seedUser();

        // manual 등록
        transactionTemplate.executeWithoutResult(tx ->
                userInterestRepository.save(UserInterest.createManual(
                        userId, InterestType.SECTOR.name(), "SEMICON", BigDecimal.valueOf(5))));

        // 피드백 upsert (manual_registered=FALSE)
        transactionTemplate.executeWithoutResult(tx ->
                userInterestRepository.upsertWeight(
                        userId, InterestType.SECTOR.name(), "SEMICON", BigDecimal.valueOf(2)));

        Number total = (Number) entityManager
                .createNativeQuery("SELECT COUNT(*) FROM user_interest WHERE user_id = ? AND interest_value = ?")
                .setParameter(1, userId)
                .setParameter(2, "SEMICON")
                .getSingleResult();
        assertThat(total.longValue()).isEqualTo(2L);

        List<UserInterest> manualOnly = userInterestRepository
                .findByUserIdAndInterestTypeAndManualRegisteredTrue(userId, InterestType.SECTOR.name());
        assertThat(manualOnly).hasSize(1);
        assertThat(manualOnly.get(0).getWeight()).isEqualByComparingTo(new BigDecimal("5"));
    }

    @Test
    @DisplayName("manual 삭제 → 피드백 upsert → 재등록해도 unique 제약 충돌 없이 동작한다")
    void reregister_doesNotCollideWithFeedbackRow() {
        seedUser();

        // 최초 manual 등록
        transactionTemplate.executeWithoutResult(tx ->
                userInterestRepository.save(UserInterest.createManual(
                        userId, InterestType.SECTOR.name(), "SEMICON", BigDecimal.valueOf(5))));

        // 해제 (수동 row만 삭제)
        transactionTemplate.executeWithoutResult(tx ->
                userInterestRepository.deleteByUserIdAndInterestTypeAndInterestValueAndManualRegisteredTrue(
                        userId, InterestType.SECTOR.name(), "SEMICON"));

        // 피드백 upsert로 manual=FALSE row 생성
        transactionTemplate.executeWithoutResult(tx ->
                userInterestRepository.upsertWeight(
                        userId, InterestType.SECTOR.name(), "SEMICON", BigDecimal.valueOf(3)));

        // 재등록 — unique 충돌 없이 성공해야 한다
        transactionTemplate.executeWithoutResult(tx ->
                userInterestRepository.save(UserInterest.createManual(
                        userId, InterestType.SECTOR.name(), "SEMICON", BigDecimal.valueOf(5))));

        List<UserInterest> manualOnly = userInterestRepository
                .findByUserIdAndInterestTypeAndManualRegisteredTrue(userId, InterestType.SECTOR.name());
        assertThat(manualOnly).hasSize(1);

        Number total = (Number) entityManager
                .createNativeQuery("SELECT COUNT(*) FROM user_interest WHERE user_id = ? AND interest_value = ?")
                .setParameter(1, userId)
                .setParameter(2, "SEMICON")
                .getSingleResult();
        assertThat(total.longValue()).isEqualTo(2L);
    }

    private void seedUser() {
        transactionTemplate.executeWithoutResult(tx ->
                entityManager.createNativeQuery(
                                "INSERT INTO users(user_id, email, nickname, password) VALUES (?, ?, ?, ?) ON CONFLICT (user_id) DO NOTHING")
                        .setParameter(1, userId)
                        .setParameter(2, "coexist-" + userId + "@example.com")
                        .setParameter(3, "coex")
                        .setParameter(4, "x")
                        .executeUpdate());
    }
}
