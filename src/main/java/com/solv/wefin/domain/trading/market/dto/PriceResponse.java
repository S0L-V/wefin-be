package com.solv.wefin.domain.trading.market.dto;

public record PriceResponse(
        String stockCode,
        int currentPrice,
        int changePrice,
        float changeRate,
        long volume,
        int openPrice,
        int highPrice,
        int lowPrice
) {
    public static PriceResponse from(String stockCode, HantuPriceApiResponse.Output output) {
        return new PriceResponse(
                stockCode,
                Integer.parseInt(output.stck_prpr()),
                Integer.parseInt(output.prdy_vrss()),
                Float.parseFloat(output.prdy_ctrt()),
                Long.parseLong(output.acml_vol()),
                Integer.parseInt(output.stck_oprc()),
                Integer.parseInt(output.stck_hgpr()),
                Integer.parseInt(output.stck_lwpr())
        );
    }
}
