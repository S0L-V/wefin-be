package com.solv.wefin.web.chat.common.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ChatSpamResponse {
    private String code;
    private String message;
    private Long remainingSeconds;
}
