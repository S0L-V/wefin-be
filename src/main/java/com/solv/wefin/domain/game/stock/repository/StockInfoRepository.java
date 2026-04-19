package com.solv.wefin.domain.game.stock.repository;

import com.solv.wefin.domain.game.stock.entity.StockInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface StockInfoRepository extends JpaRepository<StockInfo, String> {

    List<StockInfo> findByMarket(String market);

    /** 섹터 목록 + 섹터별 키워드 개수 조회 */
    @Query("SELECT si.sector AS sector, COUNT(DISTINCT si.keyword) AS keywordCount " +
            "FROM StockInfo si " +
            "WHERE si.sector IS NOT NULL AND si.keyword IS NOT NULL " +
            "GROUP BY si.sector " +
            "ORDER BY si.sector")
    List<SectorKeywordCount> findSectorsWithKeywordCount();

    /** 특정 섹터의 키워드 목록 조회 */
    @Query("SELECT DISTINCT si.keyword FROM StockInfo si " +
            "WHERE si.sector = :sector AND si.keyword IS NOT NULL " +
            "ORDER BY si.keyword")
    List<String> findKeywordsBySector(@Param("sector") String sector);

    /** 특정 섹터+키워드에 해당하는 종목 목록 (이름순) */
    List<StockInfo> findBySectorAndKeywordOrderByStockNameAsc(String sector, String keyword);

    /** 섹터별 키워드 개수 projection */
    interface SectorKeywordCount {
        String getSector();
        Long getKeywordCount();
    }
}
