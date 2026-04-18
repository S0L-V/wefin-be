package com.solv.wefin.domain.chat.common.exception;

import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import lombok.Getter;

@Getter
public class ChatSpamException extends BusinessException {
    private final String blockKey;
    private final long remainingSeconds;

    public ChatSpamException(String blockKey, long remainingSeconds) {
        super(
                ErrorCode.CHAT_SPAM_DETECTED,
                "도배가 감지되었습니다. " + remainingSeconds + "초 후에 다시 시도해주세요."
        );
        this.blockKey = blockKey;
        this.remainingSeconds = remainingSeconds;
    }

}
