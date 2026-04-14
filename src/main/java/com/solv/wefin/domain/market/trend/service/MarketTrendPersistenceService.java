package com.solv.wefin.domain.market.trend.service;

import com.solv.wefin.domain.market.trend.dto.MarketTrendContent;
import com.solv.wefin.domain.market.trend.entity.MarketTrend;
import com.solv.wefin.domain.market.trend.repository.MarketTrendRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * 오늘의 금융 동향 저장을 전담하는 서비스 (트랜잭션 경계)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketTrendPersistenceService {

    private final MarketTrendRepository marketTrendRepository;

    /**
     * 오늘 세션의 동향을 upsert한다 (native ON CONFLICT)
     */
    @Transactional
    public void upsertDailyTrend(LocalDate trendDate, MarketTrendContent content,
                                 String insightCardsJson, String relatedKeywordsJson,
                                 String sourceClusterIdsJson, int sourceArticleCount) {
        marketTrendRepository.upsertDaily(
                trendDate, MarketTrend.SESSION_DAILY,
                content.title(), content.summary(),
                insightCardsJson, relatedKeywordsJson,
                sourceClusterIdsJson, sourceArticleCount);
        log.info("[MarketTrend] 오늘 동향 upsert 완료 — trendDate: {}, sourceClusters: {}, sourceArticles: {}",
                trendDate, content.insightCards().size(), sourceArticleCount);
    }
}
