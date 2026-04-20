package com.solv.wefin.global.config;

/**
 * PostgreSQL advisory lock 키 중앙 레지스트리
 *
 * PostgreSQL advisory lock
 *   - DB 가 제공하는 애플리케이션 정의 lock
 *   - 같은 PostgreSQL 인스턴스에 붙어있는 모든 커넥션/프로세스가 공유하는 전역 락이라,
 *   - 다중 인스턴스 환경에서도 한 번에 하나만 실행 보장 가능
 *
 * 트랜잭션 단위 사용 패턴
 *   1) 락 획득 시도. 이미 다른 세션이 잡고 있으면 false 반환 (대기 X)
 *   2) 실제 배치 작업 수행
 *   3) 트랜잭션 commit/rollback 시 락 자동 해제. 명시적 unlock 불필요
 *
 * 가독성을 위해 6자리 숫자 범위에서 기능별 접두어를 둔다:
 *  - 100_xxx — 뉴스/클러스터 도메인
 */
public final class AdvisoryLockKeys {

    /** 뉴스 클러스터 조회수 랭킹(sort=view) 집계 배치 — 2026-04-20 도입 */
    public static final long HOT_NEWS_AGG = 100_001L;

    private AdvisoryLockKeys() {
    }
}
