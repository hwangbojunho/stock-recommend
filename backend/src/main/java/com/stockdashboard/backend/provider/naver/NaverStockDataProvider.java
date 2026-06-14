package com.stockdashboard.backend.provider.naver;

import com.stockdashboard.backend.domain.StockMetrics;
import com.stockdashboard.backend.exception.StockDataFetchException;
import com.stockdashboard.backend.provider.StockDataProvider;
import com.stockdashboard.backend.provider.naver.dto.NaverBasicResponse;
import com.stockdashboard.backend.provider.naver.dto.NaverFinanceResponse;
import com.stockdashboard.backend.provider.naver.dto.NaverIntegrationResponse;
import com.stockdashboard.backend.provider.naver.dto.NaverRankingResponse;
import com.stockdashboard.backend.provider.naver.dto.NaverTrendResponse;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 네이버 금융 모바일 API(m.stock.naver.com)를 이용해 종목의 시세 및 PER/PBR/PSR 등을 조회한다.
 */
@Component
public class NaverStockDataProvider implements StockDataProvider {

    private static final String BASIC_URL = "https://m.stock.naver.com/api/stock/{code}/basic";
    private static final String INTEGRATION_URL = "https://m.stock.naver.com/api/stock/{code}/integration";
    private static final String FINANCE_ANNUAL_URL = "https://m.stock.naver.com/api/stock/{code}/finance/annual";
    private static final String RANKING_URL = "https://m.stock.naver.com/api/stocks/marketValue/KOSPI?page=1&pageSize={count}";
    private static final String RANKING_PAGE_URL = "https://m.stock.naver.com/api/stocks/marketValue/KOSPI?page={page}&pageSize={pageSize}";
    private static final int RANKING_PAGE_SIZE = 100;
    private static final int RANKING_MAX_PAGES = 30;
    private static final String STOCK_END_TYPE = "stock";
    private static final String TREND_URL = "https://m.stock.naver.com/api/stock/{code}/trend?pageSize={count}";
    private static final int RECENT_TRADING_DAYS_1M = 22;

    private static final String REVENUE_ROW_TITLE = "매출액";
    private static final String OPERATING_INCOME_ROW_TITLE = "영업이익";
    private static final String NET_INCOME_ROW_TITLE = "당기순이익";
    private static final String ROE_ROW_TITLE = "ROE";
    private static final String OPERATING_MARGIN_ROW_TITLE = "영업이익률";
    private static final String NET_MARGIN_ROW_TITLE = "순이익률";
    private static final String DEBT_RATIO_ROW_TITLE = "부채비율";
    private static final int RECENT_TRADING_DAYS = 5;

    /**
     * 네이버 금융 업종코드(industryCode) 중 은행/금융지주/증권/보험/카드(여신전문) 등 금융업.
     * 이 업종은 부채비율(예금 등이 부채로 잡혀 의미가 다름)·PSR(매출 개념이 다름)을 일반 제조업과
     * 같은 기준으로 평가할 수 없어 점수 산정에서 별도 처리한다.
     */
    private static final Set<String> FINANCIAL_INDUSTRY_CODES = Set.of("301", "315", "321", "330", "337");

    private final RestClient restClient;

    public NaverStockDataProvider(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public StockMetrics getStockMetrics(String code) {
        try {
            NaverBasicResponse basic = fetch(BASIC_URL, code, NaverBasicResponse.class);
            NaverIntegrationResponse integration = fetch(INTEGRATION_URL, code, NaverIntegrationResponse.class);
            NaverFinanceResponse finance = fetch(FINANCE_ANNUAL_URL, code, NaverFinanceResponse.class);

            Map<String, String> totalInfoValues = toValueMap(integration);
            FinancialRow revenueInfo = extractLatestActual(finance, REVENUE_ROW_TITLE);
            FinancialRow roeInfo = extractLatestActual(finance, ROE_ROW_TITLE);
            FinancialRow operatingMarginInfo = extractLatestActual(finance, OPERATING_MARGIN_ROW_TITLE);
            FinancialRow netMarginInfo = extractLatestActual(finance, NET_MARGIN_ROW_TITLE);
            FinancialRow debtRatioInfo = extractLatestActual(finance, DEBT_RATIO_ROW_TITLE);

            BigDecimal price = KoreanNumberParser.parseDecimal(basic.closePrice());
            BigDecimal changeRate = KoreanNumberParser.parseDecimal(basic.fluctuationsRatio());
            BigDecimal marketCap = KoreanNumberParser.parseEokWon(totalInfoValues.get("marketValue"));
            BigDecimal per = KoreanNumberParser.parseDecimal(totalInfoValues.get("per"));
            BigDecimal pbr = KoreanNumberParser.parseDecimal(totalInfoValues.get("pbr"));
            BigDecimal eps = KoreanNumberParser.parseDecimal(totalInfoValues.get("eps"));
            BigDecimal bps = KoreanNumberParser.parseDecimal(totalInfoValues.get("bps"));
            BigDecimal forwardPer = KoreanNumberParser.parseDecimal(totalInfoValues.get("cnsPer"));
            BigDecimal forwardEps = KoreanNumberParser.parseDecimal(totalInfoValues.get("cnsEps"));
            BigDecimal psr = divide(marketCap, revenueInfo.value());
            BigDecimal forwardNetIncome = divide(marketCap, forwardPer);
            String forwardPeriod = extractForwardPeriod(finance);

            BigDecimal dividendYield = KoreanNumberParser.parseDecimal(totalInfoValues.get("dividendYieldRatio"));
            BigDecimal foreignOwnershipRatio = KoreanNumberParser.parseDecimal(totalInfoValues.get("foreignRate"));
            BigDecimal week52High = KoreanNumberParser.parseDecimal(totalInfoValues.get("highPriceOf52Weeks"));
            BigDecimal week52Low = KoreanNumberParser.parseDecimal(totalInfoValues.get("lowPriceOf52Weeks"));
            BigDecimal institutionalNetBuyRatio = extractInstitutionalNetBuyRatio(integration);
            BigDecimal institutionalNetBuyRatio1m = fetchInstitutionalNetBuyRatio1m(code);

            BigDecimal revenueGrowth3y = extractGrowth3y(finance, REVENUE_ROW_TITLE);
            BigDecimal operatingIncomeGrowth3y = extractGrowth3y(finance, OPERATING_INCOME_ROW_TITLE);
            BigDecimal netIncomeGrowth3y = extractGrowth3y(finance, NET_INCOME_ROW_TITLE);

            boolean financialSector = FINANCIAL_INDUSTRY_CODES.contains(integration.industryCode());

            return new StockMetrics(
                    basic.itemCode(),
                    basic.stockName(),
                    basic.stockExchangeName(),
                    financialSector,
                    price,
                    changeRate,
                    marketCap,
                    per,
                    pbr,
                    psr,
                    eps,
                    bps,
                    forwardPer,
                    forwardEps,
                    forwardNetIncome,
                    forwardPeriod,
                    revenueInfo.value(),
                    revenueInfo.period(),
                    roeInfo.value(),
                    operatingMarginInfo.value(),
                    netMarginInfo.value(),
                    debtRatioInfo.value(),
                    dividendYield,
                    foreignOwnershipRatio,
                    week52High,
                    week52Low,
                    institutionalNetBuyRatio,
                    institutionalNetBuyRatio1m,
                    revenueGrowth3y,
                    operatingIncomeGrowth3y,
                    netIncomeGrowth3y,
                    Instant.now()
            );
        } catch (RestClientException e) {
            throw new StockDataFetchException("네이버 금융 데이터를 가져오는 중 오류가 발생했습니다. code=" + code, e);
        }
    }

    @Override
    public List<String> getTopKospiCodes(int count) {
        try {
            NaverRankingResponse ranking = restClient.get()
                    .uri(RANKING_URL, count)
                    .retrieve()
                    .body(NaverRankingResponse.class);
            if (ranking == null || ranking.stocks() == null) {
                return List.of();
            }
            return ranking.stocks().stream()
                    .map(NaverRankingResponse.StockItem::itemCode)
                    .toList();
        } catch (RestClientException e) {
            throw new StockDataFetchException("코스피 시가총액 순위를 가져오는 중 오류가 발생했습니다.", e);
        }
    }

    @Override
    public List<String> getAllKospiCodes() {
        try {
            List<String> codes = new ArrayList<>();
            for (int page = 1; page <= RANKING_MAX_PAGES; page++) {
                NaverRankingResponse ranking = restClient.get()
                        .uri(RANKING_PAGE_URL, page, RANKING_PAGE_SIZE)
                        .retrieve()
                        .body(NaverRankingResponse.class);
                if (ranking == null || ranking.stocks() == null || ranking.stocks().isEmpty()) {
                    break;
                }
                ranking.stocks().stream()
                        .filter(item -> STOCK_END_TYPE.equals(item.stockEndType()))
                        .map(NaverRankingResponse.StockItem::itemCode)
                        .forEach(codes::add);
                if (ranking.stocks().size() < RANKING_PAGE_SIZE) {
                    break;
                }
            }
            return codes;
        } catch (RestClientException e) {
            throw new StockDataFetchException("코스피 전체 종목 목록을 가져오는 중 오류가 발생했습니다.", e);
        }
    }

    private <T> T fetch(String url, String code, Class<T> type) {
        return restClient.get()
                .uri(url, code)
                .retrieve()
                .body(type);
    }

    private Map<String, String> toValueMap(NaverIntegrationResponse integration) {
        if (integration.totalInfos() == null) {
            return Map.of();
        }
        return integration.totalInfos().stream()
                .collect(Collectors.toMap(
                        NaverIntegrationResponse.TotalInfo::code,
                        NaverIntegrationResponse.TotalInfo::value,
                        (first, second) -> first
                ));
    }

    /**
     * 연간 재무 정보에서 가장 최근 확정(추정치가 아닌) 연도의 행(매출액, ROE 등) 값을 찾는다.
     */
    private FinancialRow extractLatestActual(NaverFinanceResponse finance, String rowTitle) {
        if (finance.financeInfo() == null) {
            return FinancialRow.EMPTY;
        }
        List<NaverFinanceResponse.TrTitle> trTitles = finance.financeInfo().trTitleList();
        List<NaverFinanceResponse.Row> rows = finance.financeInfo().rowList();
        if (trTitles == null || rows == null) {
            return FinancialRow.EMPTY;
        }

        NaverFinanceResponse.TrTitle latestActual = trTitles.stream()
                .filter(title -> !"Y".equals(title.isConsensus()))
                .reduce((first, second) -> second)
                .orElse(null);
        if (latestActual == null) {
            return FinancialRow.EMPTY;
        }

        return rows.stream()
                .filter(row -> rowTitle.equals(row.title()))
                .findFirst()
                .map(row -> row.columns().get(latestActual.key()))
                .map(column -> new FinancialRow(
                        KoreanNumberParser.parseDecimal(column.value()),
                        latestActual.title()
                ))
                .orElse(FinancialRow.EMPTY);
    }

    /**
     * 최근 3개 연간 확정 실적(추정치 제외) 중 가장 오래된 값과 최신 값으로 2년 CAGR(%)을 계산한다.
     * 기준 값이 0 이하이면 성장률을 의미 있게 계산할 수 없어 null을 반환한다.
     */
    private BigDecimal extractGrowth3y(NaverFinanceResponse finance, String rowTitle) {
        if (finance.financeInfo() == null) {
            return null;
        }
        List<NaverFinanceResponse.TrTitle> trTitles = finance.financeInfo().trTitleList();
        List<NaverFinanceResponse.Row> rows = finance.financeInfo().rowList();
        if (trTitles == null || rows == null) {
            return null;
        }

        List<NaverFinanceResponse.TrTitle> actualTitles = trTitles.stream()
                .filter(title -> !"Y".equals(title.isConsensus()))
                .toList();
        if (actualTitles.size() < 2) {
            return null;
        }
        NaverFinanceResponse.TrTitle oldest = actualTitles.get(0);
        NaverFinanceResponse.TrTitle latest = actualTitles.get(actualTitles.size() - 1);
        int years = actualTitles.size() - 1;

        NaverFinanceResponse.Row row = rows.stream()
                .filter(r -> rowTitle.equals(r.title()))
                .findFirst()
                .orElse(null);
        if (row == null) {
            return null;
        }

        BigDecimal oldestValue = parseColumn(row, oldest.key());
        BigDecimal latestValue = parseColumn(row, latest.key());
        if (oldestValue == null || latestValue == null || oldestValue.signum() <= 0 || latestValue.signum() <= 0) {
            return null;
        }

        double ratio = latestValue.doubleValue() / oldestValue.doubleValue();
        double cagr = (Math.pow(ratio, 1.0 / years) - 1) * 100;
        return new BigDecimal(cagr, new MathContext(4)).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal parseColumn(NaverFinanceResponse.Row row, String key) {
        NaverFinanceResponse.Column column = row.columns().get(key);
        return column == null ? null : KoreanNumberParser.parseDecimal(column.value());
    }

    /**
     * 최근 {@value RECENT_TRADING_DAYS}거래일 외국인+기관 순매수량 합을 같은 기간 거래량 합으로 나눈 비율(%).
     */
    private BigDecimal extractInstitutionalNetBuyRatio(NaverIntegrationResponse integration) {
        if (integration.dealTrendInfos() == null || integration.dealTrendInfos().isEmpty()) {
            return null;
        }
        List<DailyFlow> flows = integration.dealTrendInfos().stream()
                .map(deal -> new DailyFlow(deal.foreignerPureBuyQuant(), deal.organPureBuyQuant(), deal.accumulatedTradingVolume()))
                .toList();
        return computeNetBuyRatio(flows, RECENT_TRADING_DAYS);
    }

    /**
     * 최근 {@value RECENT_TRADING_DAYS_1M}거래일(약 1개월) 외국인+기관 순매수량 합을 같은 기간 거래량 합으로 나눈 비율(%).
     * 5거래일 수급보다 단기 노이즈에 덜 민감한 중기 수급 지표로 사용한다.
     */
    private BigDecimal fetchInstitutionalNetBuyRatio1m(String code) {
        List<NaverTrendResponse> trend = restClient.get()
                .uri(TREND_URL, code, RECENT_TRADING_DAYS_1M)
                .retrieve()
                .body(new ParameterizedTypeReference<List<NaverTrendResponse>>() {
                });
        if (trend == null || trend.isEmpty()) {
            return null;
        }
        List<DailyFlow> flows = trend.stream()
                .map(deal -> new DailyFlow(deal.foreignerPureBuyQuant(), deal.organPureBuyQuant(), deal.accumulatedTradingVolume()))
                .toList();
        return computeNetBuyRatio(flows, RECENT_TRADING_DAYS_1M);
    }

    private BigDecimal computeNetBuyRatio(List<DailyFlow> flows, int maxDays) {
        BigDecimal netBuySum = BigDecimal.ZERO;
        BigDecimal volumeSum = BigDecimal.ZERO;
        for (DailyFlow flow : flows.subList(0, Math.min(maxDays, flows.size()))) {
            BigDecimal foreignerBuy = KoreanNumberParser.parseDecimal(flow.foreignerPureBuyQuant());
            BigDecimal organBuy = KoreanNumberParser.parseDecimal(flow.organPureBuyQuant());
            BigDecimal volume = KoreanNumberParser.parseDecimal(flow.accumulatedTradingVolume());
            if (foreignerBuy != null) {
                netBuySum = netBuySum.add(foreignerBuy);
            }
            if (organBuy != null) {
                netBuySum = netBuySum.add(organBuy);
            }
            if (volume != null) {
                volumeSum = volumeSum.add(volume);
            }
        }

        if (volumeSum.signum() == 0) {
            return null;
        }
        return netBuySum.multiply(BigDecimal.valueOf(100)).divide(volumeSum, 2, RoundingMode.HALF_UP);
    }

    /**
     * 일별 외국인/기관 순매수 수량 및 거래량(원본 문자열, KoreanNumberParser로 파싱 필요).
     */
    private record DailyFlow(String foreignerPureBuyQuant, String organPureBuyQuant, String accumulatedTradingVolume) {
    }

    /**
     * 추정PER/추정EPS(cnsPer/cnsEps)가 어느 회계연도 컨센서스인지를 연간 재무 정보에서 찾는다.
     */
    private String extractForwardPeriod(NaverFinanceResponse finance) {
        if (finance.financeInfo() == null || finance.financeInfo().trTitleList() == null) {
            return null;
        }
        return finance.financeInfo().trTitleList().stream()
                .filter(title -> "Y".equals(title.isConsensus()))
                .map(NaverFinanceResponse.TrTitle::title)
                .findFirst()
                .orElse(null);
    }

    private BigDecimal divide(BigDecimal dividend, BigDecimal divisor) {
        if (dividend == null || divisor == null || divisor.signum() == 0) {
            return null;
        }
        return dividend.divide(divisor, 4, RoundingMode.HALF_UP);
    }

    private record FinancialRow(BigDecimal value, String period) {
        static final FinancialRow EMPTY = new FinancialRow(null, null);
    }
}
