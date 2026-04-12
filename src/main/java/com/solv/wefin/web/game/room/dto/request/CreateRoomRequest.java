package com.solv.wefin.web.game.room.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CreateRoomRequest {

    @NotNull(message="시드머니 오류")
    @DecimalMin(value = "1", message = "시드머니는 0보다 커야합니다")
    @Digits(integer = 16, fraction = 2, message = "시드머니는 정수 16자리, 소수 2자리 이내여야 합니다")
    private BigDecimal seedMoney;

    @NotNull(message="기간 설정 오류")
    @Min(value = 1, message = "게임 기간은 0보다 커야 합니다.")
    @Max(value = 47, message = "게임 기간은 47개월을 초과할 수 없습니다.")
    private Integer periodMonths;

    @NotNull(message = "턴당 이동 오류")
    @Min(value = 1, message = "턴당 이동 일수는 0보다 커야 합니다.")
    private Integer moveDays;

}
