package com.solv.wefin.domain.trading.watchlist.service;

import com.solv.wefin.domain.trading.market.dto.PriceResponse;
import com.solv.wefin.domain.trading.market.service.MarketService;
import com.solv.wefin.domain.trading.stock.entity.Stock;
import com.solv.wefin.domain.trading.stock.repository.StockRepository;
import com.solv.wefin.domain.trading.watchlist.dto.WatchlistInfo;
import com.solv.wefin.domain.trading.watchlist.entity.InterestType;
import com.solv.wefin.domain.trading.watchlist.entity.UserInterest;
import com.solv.wefin.domain.trading.watchlist.repository.UserInterestRepository;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WatchlistServiceTest {

    @Mock private UserInterestRepository userInterestRepository;
    @Mock private StockRepository stockRepository;
    @Mock private MarketService marketService;
    @InjectMocks private WatchlistService watchlistService;

    private final UUID userId = UUID.randomUUID();

    private Stock mockStock() {
        Stock stock = mock(Stock.class);
        lenient().when(stock.getStockCode()).thenReturn("005930");
        lenient().when(stock.getStockName()).thenReturn("삼성전자");
        lenient().when(stock.getSector()).thenReturn("반도체");
        return stock;
    }

    private PriceResponse mockPriceResponse() {
        return new PriceResponse(
                "005930",
                BigDecimal.valueOf(206000),
                BigDecimal.valueOf(2000),
                0.98f,
                15756820L,
                BigDecimal.valueOf(208500),
                BigDecimal.valueOf(211000),
                BigDecimal.valueOf(206000)
        );
    }

    @Nested
    @DisplayName("관심종목 추가")
    class AddUserInterest {

        @Test
        @DisplayName("정상 추가 — STOCK 1건 INSERT")
        void success() {
            // given
            when(stockRepository.existsByStockCode("005930")).thenReturn(true);
            when(userInterestRepository.existsByUserIdAndInterestTypeAndInterestValue(
                    userId, InterestType.STOCK, "005930"))
                    .thenReturn(false);
            when(userInterestRepository.countByUserIdAndInterestType(
                    userId, InterestType.STOCK))
                    .thenReturn(0L);

            // when
            watchlistService.addUserInterest(userId, "005930");

            // then
            ArgumentCaptor<UserInterest> captor = ArgumentCaptor.forClass(UserInterest.class);
            verify(userInterestRepository).save(captor.capture());

            UserInterest saved = captor.getValue();
            assertThat(saved.getInterestType()).isEqualTo(InterestType.STOCK);
            assertThat(saved.getInterestValue()).isEqualTo("005930");
        }

        @Test
        @DisplayName("이미 등록된 종목 — INTEREST_ALREADY_EXISTS")
        void alreadyExists() {
            // given
            when(stockRepository.existsByStockCode("005930")).thenReturn(true);
            when(userInterestRepository.existsByUserIdAndInterestTypeAndInterestValue(
                    userId, InterestType.STOCK, "005930"))
                    .thenReturn(true);

            // when & then
            assertThatThrownBy(() -> watchlistService.addUserInterest(userId, "005930"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INTEREST_ALREADY_EXISTS);
        }

        @Test
        @DisplayName("최대 10개 초과 — INTEREST_LIMIT_EXCEEDED")
        void limitExceeded() {
            // given
            when(stockRepository.existsByStockCode("005930")).thenReturn(true);
            when(userInterestRepository.existsByUserIdAndInterestTypeAndInterestValue(
                    userId, InterestType.STOCK, "005930"))
                    .thenReturn(false);
            when(userInterestRepository.countByUserIdAndInterestType(
                    userId, InterestType.STOCK))
                    .thenReturn(10L);

            // when & then
            assertThatThrownBy(() -> watchlistService.addUserInterest(userId, "005930"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INTEREST_LIMIT_EXCEEDED);
        }
    }

    @Nested
    @DisplayName("관심종목 삭제")
    class DeleteUserInterest {

        @Test
        @DisplayName("정상 삭제")
        void success() {
            // when
            watchlistService.deleteUserInterest(userId, "005930");

            // then
            verify(userInterestRepository).deleteByUserIdAndInterestTypeAndInterestValue(
                    userId, InterestType.STOCK, "005930");
        }
    }

    @Nested
    @DisplayName("관심종목 조회")
    class GetStockList {

        @Test
        @DisplayName("현재가 포함 조회")
        void successWithPrice() {
            // given
            UserInterest interest = mock(UserInterest.class);
            when(interest.getInterestValue()).thenReturn("005930");

            when(userInterestRepository.findByUserIdAndInterestType(userId, InterestType.STOCK))
                    .thenReturn(List.of(interest));

            Stock stock = mockStock();
            when(stockRepository.findByStockCode("005930"))
                    .thenReturn(Optional.of(stock));

            PriceResponse price = mockPriceResponse();
            when(marketService.getPrice("005930"))
                    .thenReturn(price);

            // when
            List<WatchlistInfo> result = watchlistService.getStockList(userId);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).stock().getStockCode()).isEqualTo("005930");
            assertThat(result.get(0).price().currentPrice()).isEqualTo(BigDecimal.valueOf(206000));
        }
    }
}
