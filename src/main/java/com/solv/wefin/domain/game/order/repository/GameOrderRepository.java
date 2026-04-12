package com.solv.wefin.domain.game.order.repository;

import com.solv.wefin.domain.game.order.entity.GameOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface GameOrderRepository extends JpaRepository<GameOrder, UUID> {
}
