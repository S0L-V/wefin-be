package com.solv.wefin.domain.trading.market.client.dto;

import java.util.List;

public record HantuRankingApiResponse(List<Output> output) {
	public record Output(
		String data_rank, // 순위
		String mksc_shrn_iscd, // 종목코드 (6자리)
		String hts_kor_isnm, // 종목명 (한글)
		String stck_prpr, // 현재가
		String prdy_vrss, // 전일 대비
		String prdy_vrss_sign, // 부호 (1:상한 2:상승 3:보합 4:하한 5:하락
		String prdy_ctrt, // 전일대비율 (%)
		String acml_vol, // 누적 거래량
		String acml_tr_pbmn // 누적 거래대금
	) {}
}
