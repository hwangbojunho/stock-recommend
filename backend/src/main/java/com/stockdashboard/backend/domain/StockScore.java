package com.stockdashboard.backend.domain;

import java.math.BigDecimal;

/**
 * 종목의 매수 매력도를 0~100점으로 평가한 결과.
 * 각 카테고리 점수(0~10, 높을수록 긍정적)를 가중합하여 total을 산출한다.
 * 카테고리 가중치: 밸류에이션 25%, 수익성/퀄리티 20%, 성장성 18%, 수급/모멘텀 12%, 재무건전성 10%, 배당 3%
 * (추정치 변화 12%, 자체 역사 밴드/이자보상배율/외인소진율 변화 등은 시계열 데이터가 필요해 v1에서는 제외되며,
 *  나머지 항목들의 가중합을 88로 정규화하여 total을 계산한다.)
 */
public record StockScore(
        int total,
        BigDecimal valuationScore,
        BigDecimal qualityScore,
        BigDecimal growthScore,
        BigDecimal momentumScore,
        BigDecimal financialHealthScore,
        BigDecimal dividendScore
) {
}
