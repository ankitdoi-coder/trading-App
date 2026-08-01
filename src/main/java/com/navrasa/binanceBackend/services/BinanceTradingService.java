package com.navrasa.binanceBackend.services;

import com.binance.connector.client.impl.SpotClientImpl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;

@Service
public class BinanceTradingService {

    private final SpotClientImpl client;

    public BinanceTradingService(
            @Value("${binance.api.key}") String apiKey,
            @Value("${binance.api.secret}") String secretKey) {
        this.client = new SpotClientImpl(apiKey, secretKey);
    }

    /**
     * Executes a MARKET order (BUY or SELL) on Binance
     */
    public String executeOrder(String symbol, String side, double quantity) {
        LinkedHashMap<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("symbol", symbol.toUpperCase()); // e.g., BTCUSDT
        parameters.put("side", side.toUpperCase());     // "BUY" or "SELL"
        parameters.put("type", "MARKET");
        parameters.put("quantity", quantity);

        // Executes live trade on Binance and returns the raw response JSON
        return client.createTrade().newOrder(parameters);
    }
}