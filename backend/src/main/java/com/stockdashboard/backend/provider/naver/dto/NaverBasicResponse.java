package com.stockdashboard.backend.provider.naver.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * https://m.stock.naver.com/api/stock/{code}/basic 응답 중 필요한 필드만 매핑.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NaverBasicResponse(
        String itemCode,
        String stockName,
        String closePrice,
        String fluctuationsRatio,
        String stockExchangeName
) {
}
