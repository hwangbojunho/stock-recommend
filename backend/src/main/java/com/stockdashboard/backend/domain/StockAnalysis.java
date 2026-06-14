package com.stockdashboard.backend.domain;

/**
 * 종목의 원시 지표(StockMetrics)와 그에 기반한 매수 매력도 점수(StockScore)를 함께 담는다.
 */
public record StockAnalysis(StockMetrics metrics, StockScore score) {
}
