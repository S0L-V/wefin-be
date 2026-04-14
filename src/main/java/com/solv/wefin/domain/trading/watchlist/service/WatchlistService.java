package com.solv.wefin.domain.trading.watchlist.service;

import com.solv.wefin.domain.interest.service.ManualInterestLockService;
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

    public static final BigDecimal ADD_WATCHLIST_WEIGHT = new BigDecimal(5); // 관심 종목을 수동 등록할 때 부여되는 기본 weight.
    private static final int MAX_WATCHLIST_SIZE = 10; // 타입별(예: STOCK) 수동 등록 관심사의 최대 개수 제한

    private final MarketService marketService;
    private final UserInterestRepository userInterestRepository;
    private final StockRepository stockRepository;
    private final ManualInterestLockService manualInterestLockService;

    public List<WatchlistInfo> getStockList(UUID userId) {
        List<UserInterest> interests = userInterestRepository
                .findByUserIdAndInterestTypeAndManualRegisteredTrue(userId, InterestType.STOCK.name());

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

        manualInterestLockService.acquire(userId, InterestType.STOCK);

        if (userInterestRepository.existsByUserIdAndInterestTypeAndInterestValueAndManualRegisteredTrue(
                userId, InterestType.STOCK.name(), stockCode)) {
            throw new BusinessException(ErrorCode.INTEREST_ALREADY_EXISTS);
        }

        if (userInterestRepository.countByUserIdAndInterestTypeAndManualRegisteredTrue(
                userId, InterestType.STOCK.name()) >= MAX_WATCHLIST_SIZE) {
            throw new BusinessException(ErrorCode.INTEREST_LIMIT_EXCEEDED);
        }

        userInterestRepository.save(UserInterest.createManual(
                userId, InterestType.STOCK.name(), stockCode, ADD_WATCHLIST_WEIGHT));
    }

    @Transactional
    public void deleteUserInterest(UUID userId, String stockCode) {
        userInterestRepository.deleteByUserIdAndInterestTypeAndInterestValueAndManualRegisteredTrue(
                userId, InterestType.STOCK.name(), stockCode);
    }
}
