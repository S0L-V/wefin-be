package com.solv.wefin.domain.trading.stock.service;

import com.solv.wefin.domain.trading.common.StockInfoProvider;
import com.solv.wefin.domain.trading.stock.dto.StockSearchResponse;
import com.solv.wefin.domain.trading.stock.entity.Stock;
import com.solv.wefin.domain.trading.stock.repository.StockRepository;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@RequiredArgsConstructor
@Service
public class StockService implements StockInfoProvider {
    private final StockRepository stockRepository;

    @Override
    public boolean existsByCode(String stockCode) {
        return stockRepository.existsByStockCode(stockCode);
    }

    @Override
    public Stock getStock(Long stockId) {
        return stockRepository.getReferenceById(stockId);
    }

    @Override
    public String getStockName(String stockCode) {
        return findByCodeOrThrow(stockCode).getStockName();
    }

    @Override
    public String getMarket(String stockCode) {
        return findByCodeOrThrow(stockCode).getMarket();
    }

    public List<StockSearchResponse> search(String keyword, String market) {
        return stockRepository.search(keyword, market).stream()
                .map(StockSearchResponse::from)
                .toList();
    }

    private Stock findByCodeOrThrow(String stockCode) {
        return stockRepository.findByStockCode(stockCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.MARKET_STOCK_NOT_FOUND));
    }

}
