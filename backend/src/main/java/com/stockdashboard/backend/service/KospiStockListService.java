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
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 코스피 상장 전체 종목(ETF/ETN 제외, 약 950개)의 지표를 백그라운드에서 주기적으로 갱신해 DB에 영속화한다.
 *
 * <p>종목 1개당 네이버 API를 3번(basic/integration/finance) 호출하므로 전체 갱신 시 약 2,800회의
 * 외부 호출이 발생한다. 요청-응답 경로에서 이를 수행하면 너무 느리므로, 별도 스레드풀로 병렬 수집하면서
 * 종목별로 계산이 끝나는 즉시 DB에 upsert한다. 서버 기동 시 1회, 이후 매일 오후 8시(KST)에 갱신한다.
 * 이미 당일(KST) 데이터가 있는 종목은 건너뛴다.</p>
 *
 * <p>DB에 저장된 결과는 서버 재시작과 무관하게 즉시 조회되므로, 첫 접속 시 22분짜리 전체 갱신을
 * 기다릴 필요가 없다.</p>
 */
@Service
public class KospiStockListService {

    private static final Logger log = LoggerFactory.getLogger(KospiStockListService.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
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

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        Thread.ofVirtual().start(this::refresh);
    }

    @Scheduled(cron = "0 0 20 * * *", zone = "Asia/Seoul")
    public void refresh() {
        Instant start = Instant.now();
        LocalDate today = LocalDate.now(KST);
        Set<String> upToDateCodes = repository.findAll().stream()
                .filter(e -> e.getUpdatedAt() != null && LocalDate.ofInstant(e.getUpdatedAt(), KST).equals(today))
                .map(StockAnalysisEntity::getCode)
                .collect(Collectors.toSet());

        List<String> codes = stockDataProvider.getAllKospiCodes().stream()
                .filter(code -> !upToDateCodes.contains(code))
                .toList();
        int total = codes.size();
        log.info("코스피 전체 종목 갱신 시작: {}개 종목 (오늘자 데이터가 이미 있는 {}개 종목은 건너뜀)",
                total, upToDateCodes.size());

        if (total == 0) {
            log.info("모든 종목이 오늘자 데이터를 갖고 있어 갱신을 건너뜁니다.");
            return;
        }

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
                    log.info("코스피 종목 갱신 [{}/{}] {} {} ({}ms 경과)",
                            done, total, code, metrics != null ? metrics.name() : "(실패)",
                            Duration.between(start, Instant.now()).toMillis());
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
