package com.stockdashboard.backend.exception;

public class StockDataFetchException extends RuntimeException {

    public StockDataFetchException(String message, Throwable cause) {
        super(message, cause);
    }
}
