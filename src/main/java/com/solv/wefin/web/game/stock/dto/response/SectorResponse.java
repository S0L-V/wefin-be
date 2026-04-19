package com.solv.wefin.web.game.stock.dto.response;

import com.solv.wefin.domain.game.stock.repository.StockInfoRepository.SectorKeywordCount;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SectorResponse {

    private String sector;
    private Long keywordCount;

    public static SectorResponse from(SectorKeywordCount projection) {
        return new SectorResponse(projection.getSector(), projection.getKeywordCount());
    }
}
