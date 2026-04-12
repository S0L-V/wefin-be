package com.solv.wefin.domain.game.room.dto;

import java.math.BigDecimal;

public record CreateRoomCommand(BigDecimal seedMoney, Integer periodMonths, Integer moveDays) {
}
