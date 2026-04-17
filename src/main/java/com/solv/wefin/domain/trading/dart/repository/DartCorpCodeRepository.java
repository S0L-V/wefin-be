package com.solv.wefin.domain.trading.dart.repository;

import com.solv.wefin.domain.trading.dart.entity.DartCorpCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DartCorpCodeRepository extends JpaRepository<DartCorpCode, Long> {

    Optional<DartCorpCode> findByStockCode(String stockCode);
}
