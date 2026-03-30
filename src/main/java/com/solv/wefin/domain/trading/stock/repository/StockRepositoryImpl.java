package com.solv.wefin.domain.trading.stock.repository;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.solv.wefin.domain.trading.stock.entity.QStock;
import com.solv.wefin.domain.trading.stock.entity.Stock;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

import java.util.List;

import static com.solv.wefin.domain.trading.stock.entity.QStock.stock;

@RequiredArgsConstructor
public class StockRepositoryImpl implements StockRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<Stock> search(String keyword, String market) {
        QStock stock = QStock.stock;

        return queryFactory
                .selectFrom(stock)
                .where(
                        keywordContains(keyword),
                        marketEq(market)
                )
                .orderBy(stock.stockName.asc())
                .limit(20)
                .fetch();
    }

    private BooleanExpression keywordContains(String keyword) {
        if (!StringUtils.hasText(keyword)) return null;
        return stock.stockName.containsIgnoreCase(keyword)
                .or(stock.stockCode.containsIgnoreCase(keyword));
    }

    private BooleanExpression marketEq(String market) {
        return StringUtils.hasText(market) ? stock.market.eq(market) : null;
    }
}