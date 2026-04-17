package com.solv.wefin.domain.trading.market.client.dto;

import java.math.BigDecimal;

public record StockRankingItem(
	int rank,
	String stockCode,
	String stockName,
	BigDecimal currentPrice,
	BigDecimal changeRate,
	BigDecimal changeAmount,
	String changeSign,
	Long volume,
	Long tradingAmount  // 누적 거래대금
) {
	public static StockRankingItem from(HantuRankingApiResponse.Output output) {
		return new StockRankingItem(
			Integer.parseInt(output.data_rank()),
			output.mksc_shrn_iscd(),
			output.hts_kor_isnm(),
			new BigDecimal(output.stck_prpr()),
			new BigDecimal(output.prdy_ctrt()),
			new BigDecimal(output.prdy_vrss()),
			output.prdy_vrss_sign(),
			Long.parseLong(output.acml_vol()),
			(output.acml_tr_pbmn() != null && !output.acml_tr_pbmn().isBlank())
				? Long.parseLong(output.acml_tr_pbmn()) : 0L
		);
	}
}
