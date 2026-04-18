package com.solv.wefin.global.common;

import org.springframework.data.domain.Page;

/**
 * 페이징 메타 정보.
 * Spring Page의 과다한 필드(20개+)에서 클라이언트에 필요한 5개만 추출.
 * 다른 페이징 API에서도 재사용 가능.
 */
public record PageInfo(
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {
    public static PageInfo from(Page<?> page) {
        return new PageInfo(
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.hasNext());
    }
}
