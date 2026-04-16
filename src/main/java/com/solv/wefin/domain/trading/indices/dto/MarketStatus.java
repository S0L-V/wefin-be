package com.solv.wefin.domain.trading.indices.dto;

/**
 * 시장 개장 상태. Yahoo Finance meta.marketState 기준으로 매핑한다.
 */
public enum MarketStatus {
    OPEN,
    PRE_OPEN,
    CLOSED
}
