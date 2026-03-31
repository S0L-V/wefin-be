package com.solv.wefin.domain.chat.common.exception;

import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import lombok.Getter;

@Getter
public class ChatSpamException extends BusinessException {
    private final String blockKey;

    public ChatSpamException(ErrorCode errorCode, String message, String blockKey) {
        super(errorCode, message);
        this.blockKey = blockKey;
    }

}
