package com.stockdashboard.backend.provider.naver.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * https://m.stock.naver.com/api/stock/{code}/trend?pageSize={count} 응답(일별 투자자 순매수 수량 목록)의 항목.
 * 응답 자체는 이 타입의 JSON 배열이다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NaverTrendResponse(
        String bizdate,
        String foreignerPureBuyQuant,
        String organPureBuyQuant,
        String accumulatedTradingVolume
) {
}
