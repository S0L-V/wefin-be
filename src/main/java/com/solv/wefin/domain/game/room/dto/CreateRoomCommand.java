package com.solv.wefin.domain.game.room.dto;

public record CreateRoomCommand(Long seedMoney, Integer periodMonths, Integer moveDays) {
}
