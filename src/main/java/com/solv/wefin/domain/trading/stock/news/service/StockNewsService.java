package com.solv.wefin.domain.trading.stock.news.service;

import com.solv.wefin.domain.trading.stock.news.client.WefinNewsClient;
import com.solv.wefin.domain.trading.stock.news.client.dto.WefinNewsApiResponse;
import com.solv.wefin.domain.trading.stock.news.dto.StockNewsInfo;
import com.solv.wefin.domain.trading.stock.service.StockService;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StockNewsService {

    private final StockService stockService;
    private final WefinNewsClient wefinNewsClient;

    @Cacheable(cacheNames = "stockNews", key = "#stockCode")
    public StockNewsInfo getNews(String stockCode) {
        if (!stockService.existsByCode(stockCode)) {
            throw new BusinessException(ErrorCode.MARKET_STOCK_NOT_FOUND);
        }

        WefinNewsApiResponse response = wefinNewsClient.fetchClusters(stockCode);
        // 뉴스팀 응답의 status는 Integer HTTP 코드. 200 이외엔 실패.
        if (response.status() != null && response.status() != 200) {
            throw new BusinessException(ErrorCode.STOCK_NEWS_FETCH_FAILED);
        }
        return StockNewsInfo.from(response.data());
    }
}
