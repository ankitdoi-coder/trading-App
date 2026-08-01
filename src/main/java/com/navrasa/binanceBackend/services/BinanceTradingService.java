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
        
        // Use the Demo Trading URL to match the keys in your screenshot
        this.client = new SpotClientImpl(apiKey, secretKey, "https://demo-api.binance.com");
    }

    public String executeOrder(String symbol, String side, String quantity) {
        LinkedHashMap<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("symbol", symbol.toUpperCase());
        parameters.put("side", side.toUpperCase());
        parameters.put("type", "MARKET");
        parameters.put("quantity", quantity);
        // ADD THIS LINE: Increase the receive window to 60,000 milliseconds (60 seconds)
        parameters.put("recvWindow", 60000L);

        return client.createTrade().newOrder(parameters);
    }
}