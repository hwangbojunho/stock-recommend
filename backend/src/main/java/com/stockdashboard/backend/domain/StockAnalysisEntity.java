package com.stockdashboard.backend.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 코스피 전체 종목 점수 랭킹을 DB에 영속화하기 위한 {@link StockAnalysis}의 JPA 엔티티 표현.
 * {@link StockMetrics}와 {@link StockScore}의 모든 필드를 한 행에 펼쳐서 저장한다.
 */
@Entity
@Table(name = "stock_analysis")
@Getter
@Setter
@NoArgsConstructor
public class StockAnalysisEntity {

    @Id
    private String code;

    private String name;
    private String market;
    private boolean financialSector;
    private BigDecimal price;
    private BigDecimal changeRate;
    private BigDecimal marketCap;
    private BigDecimal per;
    private BigDecimal pbr;
    private BigDecimal psr;
    private BigDecimal eps;
    private BigDecimal bps;
    private BigDecimal forwardPer;
    private BigDecimal forwardEps;
    private BigDecimal forwardNetIncome;
    private String forwardPeriod;
    private BigDecimal revenue;
    private String revenuePeriod;
    private BigDecimal roe;
    private BigDecimal operatingMargin;
    private BigDecimal netMargin;
    private BigDecimal debtRatio;
    private BigDecimal dividendYield;
    private BigDecimal foreignOwnershipRatio;
    private BigDecimal week52High;
    private BigDecimal week52Low;
    private BigDecimal institutionalNetBuyRatio;
    private BigDecimal institutionalNetBuyRatio1m;
    private BigDecimal revenueGrowth3y;
    private BigDecimal operatingIncomeGrowth3y;
    private BigDecimal netIncomeGrowth3y;
    private Instant updatedAt;

    private int total;
    private BigDecimal valuationScore;
    private BigDecimal qualityScore;
    private BigDecimal growthScore;
    private BigDecimal momentumScore;
    private BigDecimal financialHealthScore;
    private BigDecimal dividendScore;

    public static StockAnalysisEntity fromDomain(StockAnalysis analysis) {
        StockMetrics m = analysis.metrics();
        StockScore s = analysis.score();

        StockAnalysisEntity entity = new StockAnalysisEntity();
        entity.setCode(m.code());
        entity.setName(m.name());
        entity.setMarket(m.market());
        entity.setFinancialSector(m.financialSector());
        entity.setPrice(m.price());
        entity.setChangeRate(m.changeRate());
        entity.setMarketCap(m.marketCap());
        entity.setPer(m.per());
        entity.setPbr(m.pbr());
        entity.setPsr(m.psr());
        entity.setEps(m.eps());
        entity.setBps(m.bps());
        entity.setForwardPer(m.forwardPer());
        entity.setForwardEps(m.forwardEps());
        entity.setForwardNetIncome(m.forwardNetIncome());
        entity.setForwardPeriod(m.forwardPeriod());
        entity.setRevenue(m.revenue());
        entity.setRevenuePeriod(m.revenuePeriod());
        entity.setRoe(m.roe());
        entity.setOperatingMargin(m.operatingMargin());
        entity.setNetMargin(m.netMargin());
        entity.setDebtRatio(m.debtRatio());
        entity.setDividendYield(m.dividendYield());
        entity.setForeignOwnershipRatio(m.foreignOwnershipRatio());
        entity.setWeek52High(m.week52High());
        entity.setWeek52Low(m.week52Low());
        entity.setInstitutionalNetBuyRatio(m.institutionalNetBuyRatio());
        entity.setInstitutionalNetBuyRatio1m(m.institutionalNetBuyRatio1m());
        entity.setRevenueGrowth3y(m.revenueGrowth3y());
        entity.setOperatingIncomeGrowth3y(m.operatingIncomeGrowth3y());
        entity.setNetIncomeGrowth3y(m.netIncomeGrowth3y());
        entity.setUpdatedAt(m.updatedAt());

        entity.setTotal(s.total());
        entity.setValuationScore(s.valuationScore());
        entity.setQualityScore(s.qualityScore());
        entity.setGrowthScore(s.growthScore());
        entity.setMomentumScore(s.momentumScore());
        entity.setFinancialHealthScore(s.financialHealthScore());
        entity.setDividendScore(s.dividendScore());
        return entity;
    }

    public StockAnalysis toDomain() {
        StockMetrics metrics = new StockMetrics(
                code, name, market, financialSector,
                price, changeRate, marketCap, per, pbr, psr, eps, bps,
                forwardPer, forwardEps, forwardNetIncome, forwardPeriod,
                revenue, revenuePeriod,
                roe, operatingMargin, netMargin, debtRatio, dividendYield, foreignOwnershipRatio,
                week52High, week52Low, institutionalNetBuyRatio, institutionalNetBuyRatio1m,
                revenueGrowth3y, operatingIncomeGrowth3y, netIncomeGrowth3y,
                updatedAt
        );
        StockScore score = new StockScore(
                total, valuationScore, qualityScore, growthScore, momentumScore, financialHealthScore, dividendScore
        );
        return new StockAnalysis(metrics, score);
    }
}
