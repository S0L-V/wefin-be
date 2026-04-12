package com.solv.wefin.domain.trading.watchlist.service;

import com.solv.wefin.domain.trading.stock.entity.Stock;
import com.solv.wefin.domain.trading.stock.repository.StockRepository;
import com.solv.wefin.domain.trading.watchlist.entity.InterestType;
import com.solv.wefin.domain.trading.watchlist.entity.UserInterest;
import com.solv.wefin.domain.trading.watchlist.repository.UserInterestRepository;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WatchlistService {

    private final UserInterestRepository userInterestRepository;
    private final StockRepository stockRepository;

    public List<UserInterest> getStockList(UUID userId) {
        return userInterestRepository.findByUserIdAndInterestType(userId, InterestType.STOCK);
    }

    @Transactional
    public void addUserInterest(UUID userId, String stockCode) {
        Stock stock = stockRepository.findByStockCode(stockCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.MARKET_STOCK_NOT_FOUND));

        if (userInterestRepository.existsByUserIdAndInterestTypeAndInterestValue(userId, InterestType.STOCK, stock.getStockName())) {
            throw new BusinessException(ErrorCode.INTEREST_ALREADY_EXISTS);
        }

        if (userInterestRepository.countByUserIdAndInterestType(userId, InterestType.STOCK) >= 10) {
            throw new BusinessException(ErrorCode.INTEREST_LIMIT_EXCEEDED);
        }

        List<UserInterest> interests = List.of(
                new UserInterest(userId, InterestType.STOCK, stock.getStockName()),
                new UserInterest(userId, InterestType.SECTOR, stock.getSector())
        );

        userInterestRepository.saveAll(interests);
    }

    @Transactional
    public void deleteUserInterest(UUID userId, String stockCode) {
        Stock stock = stockRepository.findByStockCode(stockCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.MARKET_STOCK_NOT_FOUND));

        userInterestRepository.deleteByUserIdAndInterestValue(userId, stock.getStockName());

    }
}
