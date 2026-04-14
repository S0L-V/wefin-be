package com.solv.wefin.domain.trading.trade.repository;

import java.util.List;

import com.solv.wefin.domain.trading.trade.dto.TradeSearchCondition;
import com.solv.wefin.domain.trading.trade.entity.Trade;

public interface TradeRepositoryCustom {

	List<Trade> searchTrades(Long virtualAccountId, TradeSearchCondition condition,
							 Long cursor, int size);
}
