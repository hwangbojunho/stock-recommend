package com.stockdashboard.backend.service;

import com.stockdashboard.backend.domain.StockMetrics;
import com.stockdashboard.backend.domain.StockScore;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * StockMetrics와 유니버스(코스피 시가총액 상위 종목) 내 백분위를 기반으로 매수 매력도를 0~100점으로 환산하는 v2 휴리스틱.
 *
 * <p>설계 원칙:
 * <ul>
 *   <li>밸류에이션·수익성·성장성·재무건전성·배당은 절대값이 아닌 유니버스 내 백분위로 환산한다.</li>
 *   <li>저PBR은 ROE가 일정 수준(8%) 이상일 때만 가점하여 "밸류 트랩"을 걸러낸다.</li>
 *   <li>각 항목은 0~10점(높을수록 긍정적)으로 정규화한 뒤 카테고리 내 가중합으로 카테고리 점수를 산출하고,
 *       데이터가 없는 항목은 가중치에서 제외(비례 재분배)한다.</li>
 *   <li>최종 점수는 6개 카테고리 점수를 가중합(25/20/18/12/10/3, 합 88)하여 0~100점으로 환산한다.
 *       추정치 변화(컨센서스 revision) 등 시계열 데이터가 필요한 항목은 v1에서 제외한다.</li>
 * </ul>
 */
@Component
public class StockScoreCalculator {

    private static final double NEUTRAL_SCORE = 5.0;
    private static final double ROE_TRAP_THRESHOLD = 8.0;

    /**
     * 부채비율·PSR의 백분위 비교 대상에서 금융업(은행/지주/증권/보험/카드) 종목을 제외하기 위한 필터.
     * 금융업은 예금 등이 부채로 잡혀 부채비율이 매우 높고, 매출 개념도 일반 제조업과 달라 PSR이 의미를 갖지 않는다.
     */
    private static final Predicate<StockMetrics> EXCLUDE_FINANCIAL = m -> !m.financialSector();

    private static final double VALUATION_WEIGHT = 25;
    private static final double QUALITY_WEIGHT = 20;
    private static final double GROWTH_WEIGHT = 18;
    private static final double MOMENTUM_WEIGHT = 12;
    private static final double FINANCIAL_HEALTH_WEIGHT = 10;
    private static final double DIVIDEND_WEIGHT = 3;
    private static final double TOTAL_WEIGHT =
            VALUATION_WEIGHT + QUALITY_WEIGHT + GROWTH_WEIGHT + MOMENTUM_WEIGHT + FINANCIAL_HEALTH_WEIGHT + DIVIDEND_WEIGHT;

    private final StockUniverseService universeService;

    public StockScoreCalculator(StockUniverseService universeService) {
        this.universeService = universeService;
    }

    public StockScore calculate(StockMetrics metrics) {
        double valuation = valuationScore(metrics);
        double quality = qualityScore(metrics);
        double growth = growthScore(metrics);
        double momentum = momentumScore(metrics);
        double financialHealth = financialHealthScore(metrics);
        double dividend = dividendScore(metrics);

        double total = (valuation * VALUATION_WEIGHT
                + quality * QUALITY_WEIGHT
                + growth * GROWTH_WEIGHT
                + momentum * MOMENTUM_WEIGHT
                + financialHealth * FINANCIAL_HEALTH_WEIGHT
                + dividend * DIVIDEND_WEIGHT) / TOTAL_WEIGHT;

        int roundedTotal = (int) Math.round(clamp(total * 10, 0, 100));

        return new StockScore(
                roundedTotal,
                toScore(valuation),
                toScore(quality),
                toScore(growth),
                toScore(momentum),
                toScore(financialHealth),
                toScore(dividend)
        );
    }

    /**
     * PER/PBR/PSR이 유니버스 내에서 낮을수록(저평가), 추정PER이 현재 PER보다 낮을수록(실적 성장 기대) 고득점.
     * PBR은 ROE가 {@value ROE_TRAP_THRESHOLD}% 미만이면 "저PBR=저평가" 가점을 깎아 밸류 트랩을 걸러낸다.
     */
    private double valuationScore(StockMetrics m) {
        return weightedAverage(
                new Item(7, perScore(m)),
                new Item(5, pbrScore(m)),
                new Item(3, psrScore(m)),
                new Item(6, forwardPerScore(m))
        );
    }

    private Double perScore(StockMetrics m) {
        if (m.per() == null) {
            return null;
        }
        if (m.per().signum() <= 0) {
            return 0.0;
        }
        Double percentile = universeService.percentileRank(StockScoreCalculator::positivePer, m.per());
        return percentile == null ? null : (1 - percentile) * 10;
    }

    private Double pbrScore(StockMetrics m) {
        if (m.pbr() == null) {
            return null;
        }
        if (m.pbr().signum() <= 0) {
            return 0.0;
        }
        Double percentile = universeService.percentileRank(StockScoreCalculator::positivePbr, m.pbr());
        if (percentile == null) {
            return null;
        }
        double score = (1 - percentile) * 10;

        // 밸류 트랩 방지: 저PBR(점수 > 5)인데 ROE가 낮으면 "싼 것"에 대한 가점을 깎는다.
        // ROE 데이터가 없으면 트랩 여부를 판단할 수 없으므로 가점을 깎지 않고 그대로 둔다.
        if (score > 5 && m.roe() != null) {
            double quality = clamp(m.roe().doubleValue() / ROE_TRAP_THRESHOLD, 0, 1);
            score = 5 + (score - 5) * quality;
        }
        return score;
    }

    private Double psrScore(StockMetrics m) {
        if (m.financialSector()) {
            // 금융업은 매출 개념이 일반 제조업과 달라 PSR로 평가하지 않고 가중치를 나머지 항목에 재분배한다.
            return null;
        }
        if (m.psr() == null) {
            return null;
        }
        if (m.psr().signum() <= 0) {
            return 0.0;
        }
        Double percentile = universeService.percentileRank(StockScoreCalculator::positivePsr, m.psr(), EXCLUDE_FINANCIAL);
        return percentile == null ? null : (1 - percentile) * 10;
    }

    /**
     * 현재 PER과 추정 PER을 비교해 실적 성장 기대(추정PER이 더 낮음)이면 가점, 둔화 우려면 감점.
     */
    private Double forwardPerScore(StockMetrics m) {
        if (m.per() == null || m.forwardPer() == null || m.per().signum() <= 0 || m.forwardPer().signum() <= 0) {
            return null;
        }
        double per = m.per().doubleValue();
        double forwardPer = m.forwardPer().doubleValue();
        double ratio = clamp((per - forwardPer) / per, -1, 1);
        return 5 + ratio * 5;
    }

    /**
     * ROE, 영업이익률, 순이익률이 유니버스 내에서 높을수록 고득점.
     */
    private double qualityScore(StockMetrics m) {
        return weightedAverage(
                new Item(9, percentileScore(m.roe(), StockMetrics::roe, true)),
                new Item(6, percentileScore(m.operatingMargin(), StockMetrics::operatingMargin, true)),
                new Item(5, percentileScore(m.netMargin(), StockMetrics::netMargin, true))
        );
    }

    /**
     * 최근 3개년 확정 실적 기준 매출/영업이익/순이익 성장률(CAGR)이 유니버스 내에서 높을수록, 세 지표가 함께 성장할수록 고득점.
     */
    private double growthScore(StockMetrics m) {
        return weightedAverage(
                new Item(6, percentileScore(m.operatingIncomeGrowth3y(), StockMetrics::operatingIncomeGrowth3y, true)),
                new Item(5, percentileScore(m.revenueGrowth3y(), StockMetrics::revenueGrowth3y, true)),
                new Item(4, percentileScore(m.netIncomeGrowth3y(), StockMetrics::netIncomeGrowth3y, true)),
                new Item(3, growthConsistencyScore(m))
        );
    }

    private Double growthConsistencyScore(StockMetrics m) {
        List<BigDecimal> growths = Stream.of(m.revenueGrowth3y(), m.operatingIncomeGrowth3y(), m.netIncomeGrowth3y())
                .filter(v -> v != null)
                .toList();
        if (growths.isEmpty()) {
            return null;
        }
        long positive = growths.stream().filter(v -> v.signum() > 0).count();
        return (double) positive / growths.size() * 10;
    }

    /**
     * 최근 외국인+기관 수급(5거래일·22거래일)과 52주 가격 위치(추세 추종 관점: 50~85% 구간이 가장 높은 점수)로 산출.
     * 5거래일 수급은 단기 노이즈에 취약하므로, 노이즈가 적은 22거래일(약 1개월) 수급을 더 큰 비중으로 함께 반영한다.
     */
    private double momentumScore(StockMetrics m) {
        return weightedAverage(
                new Item(2, flowScore(m.institutionalNetBuyRatio())),
                new Item(3, flowScore(m.institutionalNetBuyRatio1m())),
                new Item(4, week52PositionScore(m))
        );
    }

    private Double flowScore(BigDecimal netBuyRatio) {
        if (netBuyRatio == null) {
            return null;
        }
        return scale(netBuyRatio.doubleValue(), -3, 3, 0, 10);
    }

    /**
     * 52주 최저~최고 구간에서 현재가의 위치(%). 50~85% 구간이 만점, 95% 이상은 과열로 감점,
     * 20% 이하 바닥권은 추세 미확인으로 중립 처리한다.
     */
    private Double week52PositionScore(StockMetrics m) {
        if (m.price() == null || m.week52High() == null || m.week52Low() == null) {
            return null;
        }
        double high = m.week52High().doubleValue();
        double low = m.week52Low().doubleValue();
        if (high <= low) {
            return null;
        }
        double position = (m.price().doubleValue() - low) / (high - low) * 100;
        if (position <= 20) {
            return NEUTRAL_SCORE;
        }
        if (position <= 50) {
            return scale(position, 20, 50, 5, 10);
        }
        if (position <= 85) {
            return 10.0;
        }
        if (position <= 95) {
            return scale(position, 85, 95, 10, 5);
        }
        return scale(position, 95, 100, 5, 2);
    }

    /**
     * 부채비율이 유니버스(금융업 제외) 내에서 낮을수록 고득점.
     * 금융업(은행/지주/증권/보험/카드)은 예금 등이 부채로 잡혀 부채비율이 구조적으로 매우 높으므로
     * 일반 제조업과 같은 기준으로 평가하지 않고 중립(5점) 처리한다.
     */
    private double financialHealthScore(StockMetrics m) {
        if (m.financialSector()) {
            return NEUTRAL_SCORE;
        }
        return weightedAverage(
                new Item(7, percentileScore(m.debtRatio(), StockMetrics::debtRatio, false, EXCLUDE_FINANCIAL))
        );
    }

    /**
     * 배당수익률이 유니버스 내에서 높을수록 고득점.
     */
    private double dividendScore(StockMetrics m) {
        return weightedAverage(
                new Item(3, percentileScore(m.dividendYield(), StockMetrics::dividendYield, true))
        );
    }

    /**
     * value의 유니버스 내 백분위를 0~10점으로 환산한다. higherIsBetter가 true면 백분위가 높을수록(=값이 클수록) 고득점,
     * false면 백분위가 낮을수록(=값이 작을수록) 고득점이다.
     */
    private Double percentileScore(BigDecimal value, Function<StockMetrics, BigDecimal> extractor, boolean higherIsBetter) {
        return percentileScore(value, extractor, higherIsBetter, m -> true);
    }

    private Double percentileScore(BigDecimal value, Function<StockMetrics, BigDecimal> extractor, boolean higherIsBetter, Predicate<StockMetrics> filter) {
        if (value == null) {
            return null;
        }
        Double percentile = universeService.percentileRank(extractor, value, filter);
        if (percentile == null) {
            return null;
        }
        return higherIsBetter ? percentile * 10 : (1 - percentile) * 10;
    }

    private static BigDecimal positivePer(StockMetrics m) {
        return m.per() != null && m.per().signum() > 0 ? m.per() : null;
    }

    private static BigDecimal positivePbr(StockMetrics m) {
        return m.pbr() != null && m.pbr().signum() > 0 ? m.pbr() : null;
    }

    private static BigDecimal positivePsr(StockMetrics m) {
        return m.psr() != null && m.psr().signum() > 0 ? m.psr() : null;
    }

    /**
     * (weight, score) 목록에서 score가 null인 항목은 제외하고 나머지의 가중평균을 구한다.
     * 모든 항목이 null이면 중립값({@value NEUTRAL_SCORE})을 반환한다.
     */
    private double weightedAverage(Item... items) {
        double weightSum = 0;
        double scoreSum = 0;
        for (Item item : items) {
            if (item.score() != null) {
                weightSum += item.weight();
                scoreSum += item.weight() * item.score();
            }
        }
        return weightSum == 0 ? NEUTRAL_SCORE : scoreSum / weightSum;
    }

    private record Item(double weight, Double score) {
    }

    private double scale(double value, double lo, double hi, double scoreAtLo, double scoreAtHi) {
        double t = clamp((value - lo) / (hi - lo), 0, 1);
        return scoreAtLo + t * (scoreAtHi - scoreAtLo);
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private BigDecimal toScore(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP);
    }
}
