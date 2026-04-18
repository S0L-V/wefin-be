package com.solv.wefin.domain.chat.globalChat.dto.command;

import lombok.Builder;

@Builder
public record GlobalProfitShareCommand (
    String type,
    String userNickname,
    String stockName,
    Long profitAmount
    ) {
}
