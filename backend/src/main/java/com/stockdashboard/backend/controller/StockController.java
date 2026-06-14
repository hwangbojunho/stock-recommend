package com.stockdashboard.backend.controller;

import com.stockdashboard.backend.domain.KospiStockList;
import com.stockdashboard.backend.domain.StockAnalysis;
import com.stockdashboard.backend.service.KospiStockListService;
import com.stockdashboard.backend.service.StockService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stocks")
public class StockController {

    private final StockService stockService;
    private final KospiStockListService kospiStockListService;

    public StockController(StockService stockService, KospiStockListService kospiStockListService) {
        this.stockService = stockService;
        this.kospiStockListService = kospiStockListService;
    }

    @GetMapping("/all")
    public KospiStockList getAllStocks() {
        return kospiStockListService.getAll();
    }

    @GetMapping("/{code}/analysis")
    public StockAnalysis getStockAnalysis(@PathVariable String code) {
        return kospiStockListService.getAnalysis(code)
                .orElseGet(() -> stockService.getStockAnalysis(code));
    }
}
