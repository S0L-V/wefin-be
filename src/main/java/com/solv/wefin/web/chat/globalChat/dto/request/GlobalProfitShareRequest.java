package com.solv.wefin.web.chat.globalChat.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GlobalProfitShareRequest {
    private String type;
    private String userNickname;
    private String stockName;
    private Long profitAmount;
}
