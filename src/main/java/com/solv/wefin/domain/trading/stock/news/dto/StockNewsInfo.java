package com.solv.wefin.domain.trading.stock.news.dto;

import com.solv.wefin.domain.trading.stock.news.client.dto.WefinNewsApiResponse;

import java.util.List;

public record StockNewsInfo(
        List<Item> items,
        boolean hasNext,
        String nextCursor
) {
    public record Item(
            Long clusterId,
            String title,
            String summary,
            String thumbnailUrl,
            String publishedAt,
            Integer sourceCount,
            List<Source> sources
    ) {
    }

    public record Source(
            String publisherName,
            String url
    ) {
    }

    public static StockNewsInfo empty() {
        return new StockNewsInfo(List.of(), false, null);
    }

    public static StockNewsInfo from(WefinNewsApiResponse.Data data) {
        if (data == null) return empty();
        List<Item> items = data.items() == null
                ? List.of()
                : data.items().stream()
                        .map(StockNewsInfo::mapItem)
                        .toList();
        return new StockNewsInfo(items, data.hasNext(), data.nextCursor());
    }

    private static Item mapItem(WefinNewsApiResponse.ClusterItem c) {
        List<Source> sources = c.sources() == null
                ? List.of()
                : c.sources().stream()
                        .map(s -> new Source(s.publisherName(), s.url()))
                        .toList();
        return new Item(
                c.clusterId(),
                c.title(),
                c.summary(),
                c.thumbnailUrl(),
                c.publishedAt(),
                c.sourceCount(),
                sources
        );
    }
}
