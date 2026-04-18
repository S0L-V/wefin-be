package com.solv.wefin.global.common;

import java.util.List;
import java.util.function.Function;

public record CursorResponse<T> (
	List<T> content,
	Long nextCursor,
	boolean hasNext
) {

	public static <T, R> CursorResponse<R> from(
			List<T> items,
			int requestedSize,
			Function<T, R> mapper,
			Function<T, Long> cursorExtractor) {

		boolean hasNext = items.size() > requestedSize;
		List<T> content = hasNext ? items.subList(0, requestedSize) : items;
		Long nextCursor = hasNext ? cursorExtractor.apply(content.get(content.size() - 1)) : null;

		return new CursorResponse<>(
			content.stream().map(mapper).toList(),
			nextCursor,
			hasNext
		);
	}

	public static <T> CursorResponse<T> empty() {
		return new CursorResponse<>(List.of(), null, false);
	}
}
