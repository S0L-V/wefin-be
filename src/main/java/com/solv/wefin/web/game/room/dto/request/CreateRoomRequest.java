package com.solv.wefin.web.game.room.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CreateRoomRequest {

    @NotNull(message="시드머니 오류")
    @Min(value = 1, message = "시드머니는 0보다 커야합니다")
    private Long seedMoney;

    @NotNull(message="기간 설정 오류")
    @Min(value = 1, message = "게임 기간은 0보다 커야 합니다.")
    private Integer periodMonths;

    @NotNull(message = "턴당 이동 오류")
    @Min(value = 1, message = "턴당 이동 일수는 0보다 커야 합니다.")
    private Integer moveDays;

}
