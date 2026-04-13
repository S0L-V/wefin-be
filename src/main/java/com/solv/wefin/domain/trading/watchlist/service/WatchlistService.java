package com.solv.wefin.domain.trading.watchlist.service;

import com.solv.wefin.domain.trading.market.dto.PriceResponse;
import com.solv.wefin.domain.trading.market.service.MarketService;
import com.solv.wefin.domain.trading.stock.entity.Stock;
import com.solv.wefin.domain.trading.stock.repository.StockRepository;
import com.solv.wefin.domain.trading.watchlist.dto.WatchlistInfo;
import com.solv.wefin.domain.trading.watchlist.entity.InterestType;
import com.solv.wefin.domain.user.entity.UserInterest;
import com.solv.wefin.domain.user.repository.UserInterestRepository;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WatchlistService {

    public static final BigDecimal ADD_WATCHLIST_WEIGHT = new BigDecimal(5);
    public static final BigDecimal DELETE_WATCHLIST_WEIGHT = new BigDecimal(-5);

    private final MarketService marketService;
    private final UserInterestRepository userInterestRepository;
    private final StockRepository stockRepository;

    public List<WatchlistInfo> getStockList(UUID userId) {
        List<UserInterest> interests = userInterestRepository
                .findByUserIdAndInterestType(userId,  InterestType.STOCK.name());

        return interests.stream()
                .map(interest -> {
                    Stock stock = stockRepository.findByStockCode(interest.getInterestValue())
                            .orElseThrow(() -> new BusinessException(ErrorCode.MARKET_STOCK_NOT_FOUND));
                    PriceResponse price = marketService.getPrice(stock.getStockCode());
                    return new WatchlistInfo(stock, price);
                })
                .toList();
    }

    @Transactional
    public void addUserInterest(UUID userId, String stockCode) {
        if (!stockRepository.existsByStockCode(stockCode)) {
            throw new BusinessException(ErrorCode.MARKET_STOCK_NOT_FOUND);
        }

        if (userInterestRepository.existsByUserIdAndInterestTypeAndInterestValue(userId, InterestType.STOCK.name(), stockCode)) {
            throw new BusinessException(ErrorCode.INTEREST_ALREADY_EXISTS);
        }

        if (userInterestRepository.countByUserIdAndInterestType(userId, InterestType.STOCK.name()) >= 10) {
            throw new BusinessException(ErrorCode.INTEREST_LIMIT_EXCEEDED);
        }

        userInterestRepository.save(UserInterest.create(userId, InterestType.STOCK.name(), stockCode, ADD_WATCHLIST_WEIGHT));
    }

    @Transactional
    public void deleteUserInterest(UUID userId, String stockCode) {
        userInterestRepository.upsertWeight(userId, InterestType.STOCK.name(), stockCode, DELETE_WATCHLIST_WEIGHT);
        userInterestRepository.deleteByUserIdAndInterestTypeAndInterestValue(userId, InterestType.STOCK.name(), stockCode);
    }
}
