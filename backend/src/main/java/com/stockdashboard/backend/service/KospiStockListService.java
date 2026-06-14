package com.stockdashboard.backend.service;

import com.stockdashboard.backend.domain.KospiStockList;
import com.stockdashboard.backend.domain.StockAnalysis;
import com.stockdashboard.backend.domain.StockAnalysisEntity;
import com.stockdashboard.backend.domain.StockMetrics;
import com.stockdashboard.backend.exception.StockDataFetchException;
import com.stockdashboard.backend.provider.StockDataProvider;
import com.stockdashboard.backend.repository.StockAnalysisRepository;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 코스피 상장 전체 종목(ETF/ETN 제외, 약 950개)의 지표를 백그라운드에서 주기적으로 갱신해 DB에 영속화한다.
 *
 * <p>종목 1개당 네이버 API를 3번(basic/integration/finance) 호출하므로 전체 갱신 시 약 2,800회의
 * 외부 호출이 발생한다. 요청-응답 경로에서 이를 수행하면 너무 느리므로, 별도 스레드풀로 병렬 수집하면서
 * 종목별로 계산이 끝나는 즉시 DB에 upsert한다. 서버 기동 시 1회, 이후
 * {@value REFRESH_INTERVAL_HOURS}시간마다 갱신한다.</p>
 *
 * <p>DB에 저장된 결과는 서버 재시작과 무관하게 즉시 조회되므로, 첫 접속 시 22분짜리 전체 갱신을
 * 기다릴 필요가 없다.</p>
 */
@Service
public class KospiStockListService {

    private static final Logger log = LoggerFactory.getLogger(KospiStockListService.class);
    private static final long REFRESH_INTERVAL_HOURS = 24;
    private static final int FETCH_CONCURRENCY = 20;

    private final StockDataProvider stockDataProvider;
    private final StockScoreCalculator stockScoreCalculator;
    private final StockAnalysisRepository repository;
    private final ExecutorService executor = Executors.newFixedThreadPool(FETCH_CONCURRENCY);

    public KospiStockListService(StockDataProvider stockDataProvider, StockScoreCalculator stockScoreCalculator,
                                  StockAnalysisRepository repository) {
        this.stockDataProvider = stockDataProvider;
        this.stockScoreCalculator = stockScoreCalculator;
        this.repository = repository;
    }

    public KospiStockList getAll() {
        List<StockAnalysis> analyses = repository.findAll().stream()
                .map(StockAnalysisEntity::toDomain)
                .sorted(Comparator.comparingInt((StockAnalysis a) -> a.score().total()).reversed())
                .toList();

        Instant updatedAt = analyses.stream()
                .map(a -> a.metrics().updatedAt())
                .filter(java.util.Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);

        return new KospiStockList(analyses, updatedAt);
    }

    public Optional<StockAnalysis> getAnalysis(String code) {
        return repository.findById(code).map(StockAnalysisEntity::toDomain);
    }

    @Scheduled(initialDelay = 0, fixedDelay = REFRESH_INTERVAL_HOURS, timeUnit = TimeUnit.HOURS)
    public void refresh() {
        Instant start = Instant.now();
        List<String> codes = stockDataProvider.getAllKospiCodes();
        int total = codes.size();
        log.info("코스피 전체 종목 갱신 시작: {}개 종목", total);

        AtomicInteger progress = new AtomicInteger(0);
        AtomicInteger saved = new AtomicInteger(0);
        List<CompletableFuture<Void>> futures = codes.stream()
                .map(code -> CompletableFuture.runAsync(() -> {
                    StockMetrics metrics = tryGetMetrics(code);
                    if (metrics != null) {
                        StockAnalysis analysis = new StockAnalysis(metrics, stockScoreCalculator.calculate(metrics));
                        repository.save(StockAnalysisEntity.fromDomain(analysis));
                        saved.incrementAndGet();
                    }
                    int done = progress.incrementAndGet();
                    if (done % 50 == 0 || done == total) {
                        log.info("코스피 전체 종목 갱신 진행: {}/{} ({}ms 경과)",
                                done, total, Duration.between(start, Instant.now()).toMillis());
                    }
                }, executor))
                .toList();

        futures.forEach(CompletableFuture::join);

        log.info("코스피 전체 종목 갱신 완료: {}개 종목 ({}ms 소요)",
                saved.get(), Duration.between(start, Instant.now()).toMillis());
    }

    private StockMetrics tryGetMetrics(String code) {
        try {
            return stockDataProvider.getStockMetrics(code);
        } catch (StockDataFetchException e) {
            log.warn("종목 데이터를 가져오지 못해 건너뜁니다. code={}, reason={}", code, e.getMessage());
            return null;
        }
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
    }
}
