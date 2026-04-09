package com.solv.wefin.web.chat.groupChat.dto.request;

import jakarta.validation.constraints.NotNull;

public record ShareNewsRequest(
        @NotNull
        Long newsClusterId,
        Long replyToMessageId
) {
}
