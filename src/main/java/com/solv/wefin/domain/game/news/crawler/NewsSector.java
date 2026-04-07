package com.solv.wefin.domain.game.news.crawler;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NewsSector {

    SEMICONDUCTOR("반도체", "반도체"),
    IT("IT", "IT 소프트웨어"),
    BIO("바이오", "바이오 제약"),
    BATTERY("2차전지", "2차전지 배터리"),
    FINANCE("금융", "은행 금융"),
    AUTO("자동차", "자동차"),
    ENERGY("에너지", "에너지 화학"),
    RETAIL("유통", "유통 소비재"),
    CONSTRUCTION("건설", "건설 부동산"),
    ENTERTAINMENT("엔터", "엔터 미디어");

    private final String displayName;
    private final String searchKeyword;
}
