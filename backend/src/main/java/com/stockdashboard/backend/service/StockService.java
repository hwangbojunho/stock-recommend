package com.stockdashboard.backend.service;

import com.stockdashboard.backend.config.CacheConfig;
import com.stockdashboard.backend.domain.StockAnalysis;
import com.stockdashboard.backend.domain.StockMetrics;
import com.stockdashboard.backend.provider.StockDataProvider;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class StockService {

    private final StockDataProvider stockDataProvider;
    private final StockScoreCalculator stockScoreCalculator;

    public StockService(StockDataProvider stockDataProvider, StockScoreCalculator stockScoreCalculator) {
        this.stockDataProvider = stockDataProvider;
        this.stockScoreCalculator = stockScoreCalculator;
    }

    @Cacheable(cacheNames = CacheConfig.STOCK_METRICS_CACHE, key = "#code")
    public StockMetrics getStockMetrics(String code) {
        return stockDataProvider.getStockMetrics(code);
    }

    /**
     * 코스피 전체 종목 DB에 아직 없는 종목(신규 상장 등)에 대한 라이브 계산 fallback.
     */
    public StockAnalysis getStockAnalysis(String code) {
        StockMetrics metrics = getStockMetrics(code);
        return new StockAnalysis(metrics, stockScoreCalculator.calculate(metrics));
    }
}
