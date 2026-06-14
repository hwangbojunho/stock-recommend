package com.stockdashboard.backend.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 종목별 시세 및 PER/PBR/PSR 등 밸류에이션 지표.
 * marketCap, revenue, forwardNetIncome 단위는 억원(KRW 100,000,000).
 * roe, operatingMargin, netMargin, debtRatio, dividendYield, foreignOwnershipRatio, institutionalNetBuyRatio 단위는 %.
 * institutionalNetBuyRatio: 최근 5거래일 외국인+기관 순매수량 합 / 같은 기간 거래량 합.
 * institutionalNetBuyRatio1m: 최근 22거래일(약 1개월) 외국인+기관 순매수량 합 / 같은 기간 거래량 합.
 * revenueGrowth3y, operatingIncomeGrowth3y, netIncomeGrowth3y: 최근 3개 연간 확정 실적 기준 2년 CAGR(%).
 * financialSector: 은행/보험/증권/지주회사 등 금융업 여부. true이면 부채비율·PSR이 일반 제조업과 다른 의미를 가지므로
 * 점수 산정 시 해당 항목을 평가에서 제외(중립 처리)한다.
 */
public record StockMetrics(
        String code,
        String name,
        String market,
        boolean financialSector,
        BigDecimal price,
        BigDecimal changeRate,
        BigDecimal marketCap,
        BigDecimal per,
        BigDecimal pbr,
        BigDecimal psr,
        BigDecimal eps,
        BigDecimal bps,
        BigDecimal forwardPer,
        BigDecimal forwardEps,
        BigDecimal forwardNetIncome,
        String forwardPeriod,
        BigDecimal revenue,
        String revenuePeriod,
        BigDecimal roe,
        BigDecimal operatingMargin,
        BigDecimal netMargin,
        BigDecimal debtRatio,
        BigDecimal dividendYield,
        BigDecimal foreignOwnershipRatio,
        BigDecimal week52High,
        BigDecimal week52Low,
        BigDecimal institutionalNetBuyRatio,
        BigDecimal institutionalNetBuyRatio1m,
        BigDecimal revenueGrowth3y,
        BigDecimal operatingIncomeGrowth3y,
        BigDecimal netIncomeGrowth3y,
        Instant updatedAt
) {
}
