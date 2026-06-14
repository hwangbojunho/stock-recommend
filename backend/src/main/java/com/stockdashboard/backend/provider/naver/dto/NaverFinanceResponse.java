package com.stockdashboard.backend.provider.naver.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

/**
 * https://m.stock.naver.com/api/stock/{code}/finance/annual 응답 중 필요한 필드만 매핑.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NaverFinanceResponse(
        String itemCode,
        FinanceInfo financeInfo
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FinanceInfo(
            List<TrTitle> trTitleList,
            List<Row> rowList
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TrTitle(
            String title,
            String key,
            String isConsensus
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Row(
            String title,
            Map<String, Column> columns
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Column(
            String value
    ) {
    }
}
