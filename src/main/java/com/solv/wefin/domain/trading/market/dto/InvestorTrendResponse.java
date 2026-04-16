package com.solv.wefin.domain.trading.market.dto;

import com.solv.wefin.domain.trading.market.client.dto.HantuInvestorTrendApiResponse;
import com.solv.wefin.global.util.ParseUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 종목 투자자별(외국인/기관/개인) 일별 순매수 동향.
 * 음수면 순매도. 최신일자가 리스트 앞쪽.
 */
public record InvestorTrendResponse(
        String stockCode,
        List<Item> items
) {
    public record Item(
            LocalDate date,
            long closePrice,
            long priceChange,
            long foreignNetBuy,
            long institutionNetBuy,
            long individualNetBuy
    ) {}

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    public static InvestorTrendResponse from(String stockCode, HantuInvestorTrendApiResponse response) {
        List<HantuInvestorTrendApiResponse.Output> raw =
                response == null || response.output() == null ? List.of() : response.output();
        List<Item> items = raw.stream()
                .map(InvestorTrendResponse::toItem)
                .toList();
        return new InvestorTrendResponse(stockCode, items);
    }

    private static Item toItem(HantuInvestorTrendApiResponse.Output o) {
        return new Item(
                parseDate(o.stck_bsop_date()),
                ParseUtils.parseLong(o.stck_clpr()),
                applySign(o.prdy_vrss(), o.prdy_vrss_sign()),
                ParseUtils.parseLong(o.frgn_ntby_qty()),
                ParseUtils.parseLong(o.orgn_ntby_qty()),
                ParseUtils.parseLong(o.prsn_ntby_qty())
        );
    }

    private static LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return LocalDate.parse(raw, DATE_FORMAT);
    }

    /**
     * 한투 전일대비는 절댓값, 부호는 prdy_vrss_sign(1:상한/2:상승=+, 4:하한/5:하락=-, 3:보합=0)으로 내려온다.
     */
    private static long applySign(String rawAbs, String sign) {
        long abs = ParseUtils.parseLong(rawAbs);
        if (abs == 0 || sign == null) return abs;
        return ("4".equals(sign) || "5".equals(sign)) ? -abs : abs;
    }
}
