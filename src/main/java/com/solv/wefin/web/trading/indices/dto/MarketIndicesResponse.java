package com.solv.wefin.web.trading.indices.dto;

import com.solv.wefin.domain.trading.indices.dto.ChangeDirection;
import com.solv.wefin.domain.trading.indices.dto.IndexCode;
import com.solv.wefin.domain.trading.indices.dto.IndexQuote;
import com.solv.wefin.domain.trading.indices.dto.MarketStatus;
import com.solv.wefin.domain.trading.indices.dto.SparklinePoint;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record MarketIndicesResponse(Instant updatedAt, List<Item> indices) {

    public static MarketIndicesResponse from(List<IndexQuote> quotes) {
        List<Item> items = quotes.stream().map(Item::from).toList();
        return new MarketIndicesResponse(Instant.now(), items);
    }

    public record Item(
        IndexCode code,
        String name,
        BigDecimal currentValue,
        BigDecimal changeValue,
        BigDecimal changeRate,
        ChangeDirection changeDirection,
        boolean isDelayed,
        MarketStatus marketStatus,
        List<Point> sparkline
    ) {
        public static Item from(IndexQuote q) {
            List<Point> points = q.sparkline().stream().map(Point::from).toList();
            return new Item(
                q.code(),
                q.label(),
                q.currentValue(),
                q.changeValue(),
                q.changeRate(),
                q.changeDirection(),
                q.isDelayed(),
                q.marketStatus(),
                points
            );
        }
    }

    public record Point(Instant t, BigDecimal v) {
        public static Point from(SparklinePoint p) {
            return new Point(p.t(), p.v());
        }
    }
}
