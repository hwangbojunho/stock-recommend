package com.stockdashboard.backend.repository;

import com.stockdashboard.backend.domain.StockAnalysisEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockAnalysisRepository extends JpaRepository<StockAnalysisEntity, String> {
}
