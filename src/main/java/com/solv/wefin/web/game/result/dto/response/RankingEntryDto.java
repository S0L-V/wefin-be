package com.solv.wefin.web.game.result.dto.response;

import com.solv.wefin.domain.game.result.dto.GameResultInfo;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class RankingEntryDto {

    private int rank;
    private String userName;
    private BigDecimal finalAsset;

    /**
     * 본인 행 표시 여부.
     * Boolean wrapper 로 선언하여 Lombok 이 getIsMine() 게터를 만들도록 한다.
     * primitive boolean + 'is' prefix 필드는 isMine() 게터가 생성되어
     * Jackson 이 JSON 키를 "mine" 으로 직렬화하는 문제를 회피한다.
     */
    private Boolean isMine;

    public static RankingEntryDto from(GameResultInfo.RankingEntry entry) {
        return new RankingEntryDto(
                entry.rank(),
                entry.userName(),
                entry.finalAsset(),
                entry.isMine());
    }
}
