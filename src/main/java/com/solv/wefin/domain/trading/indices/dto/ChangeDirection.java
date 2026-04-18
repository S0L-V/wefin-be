package com.solv.wefin.domain.trading.indices.dto;

/**
 * 지수 변동 방향.
 * <p>{@code domain/market/entity/MarketSnapshot.ChangeDirection} 과 값은 같지만,
 * 타 팀 도메인(기사쪽) 의존을 피하기 위해 자체 정의한다.</p>
 */
public enum ChangeDirection {
    UP, DOWN, FLAT
}
