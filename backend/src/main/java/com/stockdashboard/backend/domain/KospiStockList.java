package com.stockdashboard.backend.domain;

import java.time.Instant;
import java.util.List;

/**
 * 코스피 상장 전체 종목(ETF/ETN 제외)의 지표 및 매수 매력도 점수 목록.
 * updatedAt이 null이면 아직 백그라운드 스케줄러의 첫 갱신이 완료되지 않은 상태(서버 기동 직후)이다.
 */
public record KospiStockList(List<StockAnalysis> stocks, Instant updatedAt) {
}
