package com.solv.wefin.domain.interest.service;

import com.solv.wefin.domain.trading.watchlist.entity.InterestType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 수동 관심사 등록 시 (userId, interestType) 단위 직렬화를 보장하는 PostgreSQL advisory lock helper.
 *
 * count + insert가 별도 쿼리로 실행되는 구조라, 서로 다른 code의 concurrent add 요청이
 * 들어오면 둘 다 한도 미만이라고 판단하고 저장해 타입별 10개 제한이 깨질 수 있다.
 * 같은 (userId, interestType) 키에 대한 요청만 대기시키고, 다른 사용자/다른 타입 요청은
 * 블로킹되지 않도록 pg_advisory_xact_lock을 트랜잭션 경계 안에서 획득한다
 */
@Component
public class ManualInterestLockService {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * 현재 트랜잭션 동안 (userId, type) 키에 대한 advisory lock(사용자 정의 락)을 획득한다.
     *
     * 트랜잭션이 커밋/롤백되면 자동 해제되므로 별도 unlock 호출은 필요 없다.
     * 반드시 {@code @Transactional} 경계 안에서 호출해야 한다
     */
    public void acquire(UUID userId, InterestType type) {
        String key = userId + ":" + type.name();
        entityManager
                .createNativeQuery("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))")
                .setParameter(1, key)
                .getSingleResult();
    }
}
