package com.solv.wefin.web.trading.trade;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.solv.wefin.domain.trading.account.entity.VirtualAccount;
import com.solv.wefin.domain.trading.account.service.VirtualAccountService;
import com.solv.wefin.domain.trading.order.entity.OrderSide;
import com.solv.wefin.domain.trading.stock.entity.Stock;
import com.solv.wefin.domain.trading.stock.service.StockService;
import com.solv.wefin.domain.trading.trade.dto.TradeSearchCondition;
import com.solv.wefin.domain.trading.trade.entity.Trade;
import com.solv.wefin.domain.trading.trade.service.TradeService;
import com.solv.wefin.global.common.ApiResponse;
import com.solv.wefin.global.common.CursorResponse;
import com.solv.wefin.web.trading.trade.dto.TradeHistoryResponse;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

@Validated
@RestController
@RequestMapping("/api/trade")
@RequiredArgsConstructor
public class TradeController {

	private final TradeService tradeService;
	private final VirtualAccountService accountService;
	private final StockService stockService;

	@GetMapping("/history")
	public ApiResponse<CursorResponse<TradeHistoryResponse>> getTradeHistory(
			@AuthenticationPrincipal UUID userId,
			@RequestParam(required = false) String stockCode,
			@RequestParam(required = false) OrderSide side,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
			@RequestParam(required = false) Long cursor,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {

		VirtualAccount account = accountService.getAccountByUserId(userId);

		Long stockId = null;
		if (stockCode != null) {
			Stock stock = stockService.findByStockCode(stockCode).orElse(null);
			if (stock == null) {
				return ApiResponse.success(CursorResponse.empty());
			}
			stockId = stock.getId();
		}

		TradeSearchCondition condition = new TradeSearchCondition(stockId, side, startDate, endDate);
		List<Trade> trades = tradeService.searchTrades(account.getVirtualAccountId(), condition, cursor, size);

		List<Long> stockIds = trades.stream().map(Trade::getStockId).distinct().toList();
		Map<Long, Stock> stockMap = stockService.findAllByIdIn(stockIds).stream()
			.collect(Collectors.toMap(Stock::getId, Function.identity()));

		return ApiResponse.success(CursorResponse.from(
			trades, size,
			trade -> {
				Stock stock = stockMap.get(trade.getStockId());
				return TradeHistoryResponse.from(trade,
					stock != null ? stock.getStockCode() : null,
					stock != null ? stock.getStockName() : null);
			},
			Trade::getTradeId
		));
	}
}
