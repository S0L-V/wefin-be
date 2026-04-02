package com.solv.wefin.web.chat.globalChat.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GlobalProfitShareRequest {
    private String type;

    @NotBlank
    private String userNickname;

    @NotBlank
    private String stockName;

    @NotNull
    private Long profitAmount;
}
