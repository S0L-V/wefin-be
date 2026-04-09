package com.solv.wefin.domain.chat.groupChat.dto.info;

public record NewsShareInfo(
        Long newsClusterId,
        String title,
        String summary,
        String thumbnailUrl
) {
}
