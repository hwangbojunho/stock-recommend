package com.stockdashboard.backend.service;

import com.stockdashboard.backend.config.CacheConfig;
import com.stockdashboard.backend.domain.StockMetrics;
import com.stockdashboard.backend.exception.StockDataFetchException;
import com.stockdashboard.backend.provider.StockDataProvider;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * 점수 산정 시 백분위 비교 기준이 되는 "유니버스"(코스피 시가총액 상위 종목)의 지표를 제공한다.
 */
@Service
public class StockUniverseService {

    private static final int UNIVERSE_SIZE = 50;

    private final StockDataProvider stockDataProvider;

    public StockUniverseService(StockDataProvider stockDataProvider) {
        this.stockDataProvider = stockDataProvider;
    }

    @Cacheable(cacheNames = CacheConfig.UNIVERSE_CODES_CACHE, sync = true)
    public List<String> getUniverseCodes() {
        return stockDataProvider.getTopKospiCodes(UNIVERSE_SIZE);
    }

    /**
     * sync = true: 캐시 미적중 시 여러 스레드가 동시에 호출하면(예: 코스피 전체 종목 갱신 시
     * 950개 종목에 대한 점수를 병렬로 계산할 때) 한 스레드만 실제로 50개 종목을 가져오고
     * 나머지는 그 결과를 기다린다. sync 없으면 캐시가 채워지기 전까지 호출마다 50종목×3API를
     * 중복 호출하는 "캐시 stampede"가 발생한다.
     */
    @Cacheable(cacheNames = CacheConfig.UNIVERSE_METRICS_CACHE, sync = true)
    public List<StockMetrics> getUniverseMetrics() {
        return getUniverseCodes().parallelStream()
                .map(this::tryGetMetrics)
                .filter(metrics -> metrics != null)
                .toList();
    }

    private StockMetrics tryGetMetrics(String code) {
        try {
            return stockDataProvider.getStockMetrics(code);
        } catch (StockDataFetchException e) {
            return null;
        }
    }

    /**
     * 유니버스 내에서 value가 차지하는 백분위(0~1)를 반환한다.
     * 1에 가까울수록 유니버스 내 다른 종목들보다 값이 크다는 의미이며, value 또는 유니버스 데이터가 없으면 null을 반환한다.
     */
    public Double percentileRank(Function<StockMetrics, BigDecimal> extractor, BigDecimal value) {
        return percentileRank(extractor, value, m -> true);
    }

    /**
     * filter를 통과하는 종목들만으로 구성한 유니버스에서 value의 백분위(0~1)를 반환한다.
     * 예: 부채비율·PSR은 금융업 종목을 제외한 유니버스를 기준으로 평가한다.
     */
    public Double percentileRank(Function<StockMetrics, BigDecimal> extractor, BigDecimal value, Predicate<StockMetrics> filter) {
        if (value == null) {
            return null;
        }
        List<BigDecimal> values = getUniverseMetrics().stream()
                .filter(filter)
                .map(extractor)
                .filter(v -> v != null)
                .toList();
        if (values.isEmpty()) {
            return null;
        }
        long countLessOrEqual = values.stream().filter(v -> v.compareTo(value) <= 0).count();
        return (double) countLessOrEqual / values.size();
    }
}
