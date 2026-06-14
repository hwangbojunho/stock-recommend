package com.stockdashboard.backend.provider.naver.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * https://m.stock.naver.com/api/stock/{code}/integration 응답 중 필요한 필드만 매핑.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NaverIntegrationResponse(
        String itemCode,
        String stockName,
        List<TotalInfo> totalInfos,
        List<DealTrendInfo> dealTrendInfos,
        String industryCode
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TotalInfo(
            String code,
            String key,
            String value,
            String valueDesc
    ) {
    }

    /**
     * 일별 투자자(외국인/기관/개인) 순매수 수량.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DealTrendInfo(
            String bizdate,
            String foreignerPureBuyQuant,
            String organPureBuyQuant,
            String individualPureBuyQuant,
            String accumulatedTradingVolume
    ) {
    }
}
