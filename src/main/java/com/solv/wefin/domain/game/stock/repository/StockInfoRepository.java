package com.solv.wefin.domain.game.stock.repository;

import com.solv.wefin.domain.game.stock.entity.StockInfo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StockInfoRepository extends JpaRepository<StockInfo, String> {

    List<StockInfo> findByMarket(String market);
}
