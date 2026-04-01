package com.solv.wefin.web.chat.globalChat.dto.request;

import jakarta.validation.constraints.NotBlank;
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

    @NonNull
    private Long profitAmount;
}
