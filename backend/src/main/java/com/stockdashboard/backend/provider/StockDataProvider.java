package com.stockdashboard.backend.provider;

import com.stockdashboard.backend.domain.StockMetrics;

import java.util.List;

/**
 * 종목 코드로 시세/밸류에이션 지표를 조회하는 데이터 소스 인터페이스.
 * 구현체를 교체하면 (예: 한국투자증권 KIS Open API) 다른 데이터 소스로 손쉽게 전환할 수 있다.
 */
public interface StockDataProvider {

    StockMetrics getStockMetrics(String code);

    /**
     * 코스피 시가총액 상위 종목 코드를 시가총액 내림차순으로 반환한다.
     * 점수 산정 시 백분위 비교 기준이 되는 유니버스를 구성하는 데 사용한다.
     */
    List<String> getTopKospiCodes(int count);

    /**
     * 코스피 상장 전체 종목(ETF/ETN 제외) 코드를 시가총액 내림차순으로 반환한다.
     */
    List<String> getAllKospiCodes();
}
