package com.solv.wefin.domain.trading.stock.service;

import com.solv.wefin.domain.trading.common.StockInfoProvider;
import com.solv.wefin.domain.trading.stock.dto.StockSearchResponse;
import com.solv.wefin.domain.trading.stock.entity.Stock;
import com.solv.wefin.domain.trading.stock.repository.StockRepository;
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
        return stockRepository.findByStockCode(stockCode)
                .orElseThrow(() -> new RuntimeException("종목 없음"))
                .getStockName();
    }

    @Override
    public String getMarket(String stockCode) {
        return stockRepository.findByStockCode(stockCode)
                .orElseThrow(() -> new RuntimeException("종목 없음"))
                .getMarket();
    }

    public List<StockSearchResponse> search(String keyword, String market) {
        return stockRepository.search(keyword, market).stream()
                .map(stock -> new StockSearchResponse(
                        stock.getStockCode(),
                        stock.getStockName(),
                        stock.getMarket(),
                        stock.getSector()
                ))
                .toList();
    }

}
