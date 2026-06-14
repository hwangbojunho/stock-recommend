package com.stockdashboard.backend.provider.naver.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * https://m.stock.naver.com/api/stocks/marketValue/{market} 응답 중 필요한 필드만 매핑.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NaverRankingResponse(List<StockItem> stocks) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StockItem(String itemCode, String stockEndType) {
    }
}
