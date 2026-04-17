package com.solv.wefin.domain.interest.service;

import com.solv.wefin.domain.trading.watchlist.entity.InterestType;
import com.solv.wefin.domain.user.entity.UserInterest;
import com.solv.wefin.domain.user.repository.UserInterestRepository;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
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
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 타입별 10개 한도가 동시 요청에서도 유지되는지 검증하는 통합 테스트.
 *
 * 기존에는 exists/count/save가 별도 쿼리로 실행되어 서로 다른 code의 concurrent add 두 건이
 * 둘 다 한도 미만으로 판단돼 11개가 저장될 수 있었다. {@link ManualInterestLockService}가
 * (userId, interestType) 단위 advisory lock을 잡아 직렬화하는지 실제 DB로 확인한다
 */
@SpringBootTest
@ActiveProfiles("test")
class InterestServiceConcurrencyIntegrationTest {

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

    @Autowired private InterestService interestService;
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
            entityManager.createNativeQuery(
                            "DELETE FROM news_article_tag WHERE tag_code LIKE 'CONCUR_%'")
                    .executeUpdate();
            entityManager.createNativeQuery(
                            "DELETE FROM news_article WHERE title LIKE 'seed-CONCUR_%'")
                    .executeUpdate();
            entityManager.createNativeQuery("DELETE FROM users WHERE user_id = ?")
                    .setParameter(1, userId).executeUpdate();
        });
    }

    @Test
    @DisplayName("동시 add 2건이 한도(10개)를 초과하지 않는다 — 한 건은 LIMIT_EXCEEDED")
    void concurrentAdds_enforceLimitAtDb() throws Exception {
        // given — 9개 수동 등록 + 2개 태그 선행 등록(allowlist 통과용)
        seedManualInterests(9);
        seedArticleTag("CONCUR_A", "Alpha");
        seedArticleTag("CONCUR_B", "Beta");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger limitBlocked = new AtomicInteger();

        try {
            pool.submit(() -> runAdd("CONCUR_A", ready, start, success, limitBlocked));
            pool.submit(() -> runAdd("CONCUR_B", ready, start, success, limitBlocked));

            ready.await();
            start.countDown();
            pool.shutdown();
            boolean terminated = pool.awaitTermination(10, TimeUnit.SECONDS);
            assertThat(terminated).isTrue();
        } finally {
            if (!pool.isTerminated()) pool.shutdownNow();
        }

        // then — 정확히 한 건만 성공, 한 건은 한도 초과로 차단
        assertThat(success.get()).isEqualTo(1);
        assertThat(limitBlocked.get()).isEqualTo(1);
        long finalCount = userInterestRepository
                .countByUserIdAndInterestTypeAndManualRegisteredTrue(userId, InterestType.SECTOR.name());
        assertThat(finalCount).isEqualTo(10L);
    }

    private void runAdd(String code, CountDownLatch ready, CountDownLatch start,
                        AtomicInteger success, AtomicInteger limitBlocked) {
        try {
            ready.countDown();
            start.await();
            interestService.add(userId, InterestType.SECTOR, code);
            success.incrementAndGet();
        } catch (BusinessException e) {
            if (e.getErrorCode() == ErrorCode.INTEREST_LIMIT_EXCEEDED) {
                limitBlocked.incrementAndGet();
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private void seedManualInterests(int count) {
        transactionTemplate.executeWithoutResult(tx -> {
            entityManager.createNativeQuery(
                            "INSERT INTO users(user_id, email, nickname, password) VALUES (?, ?, ?, ?) ON CONFLICT (user_id) DO NOTHING")
                    .setParameter(1, userId)
                    .setParameter(2, "concur-" + userId + "@example.com")
                    .setParameter(3, "concur")
                    .setParameter(4, "x")
                    .executeUpdate();
            for (int i = 0; i < count; i++) {
                String code = "CONCUR_SEED_" + i;
                seedArticleTagInternal(code, "Seed" + i);
                userInterestRepository.save(UserInterest.createManual(
                        userId, InterestType.SECTOR.name(), code, BigDecimal.valueOf(5)));
            }
        });
    }

    private void seedArticleTag(String tagCode, String tagName) {
        transactionTemplate.executeWithoutResult(tx -> seedArticleTagInternal(tagCode, tagName));
    }

    private void seedArticleTagInternal(String tagCode, String tagName) {
        // news_article_tag.news_article_id는 FK가 실제로 강제되지 않고(마이그레이션에 제약 없음)
        // Repository는 tag_type + tag_code 기준 조회만 하므로 최소 컬럼으로 더미 article을 삽입한다
        Long articleId = ((Number) entityManager.createNativeQuery(
                        "INSERT INTO news_article(publisher_name, title, original_url, created_at) " +
                                "VALUES (?, ?, ?, now()) RETURNING news_article_id")
                .setParameter(1, "seedpub")
                .setParameter(2, "seed-" + tagCode)
                .setParameter(3, "https://example.com/" + tagCode)
                .getSingleResult()).longValue();

        entityManager.createNativeQuery(
                        "INSERT INTO news_article_tag(news_article_id, tag_type, tag_code, tag_name, created_at) " +
                                "VALUES (?, 'SECTOR', ?, ?, now())")
                .setParameter(1, articleId)
                .setParameter(2, tagCode)
                .setParameter(3, tagName)
                .executeUpdate();
    }
}
