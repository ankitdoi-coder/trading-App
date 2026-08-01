package com.navrasa.binanceBackend.services;

import com.binance.connector.client.impl.SpotClientImpl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;

@Service
public class BinanceService {

    private final SpotClientImpl client;

    public BinanceService(
            @Value("${binance.api.key}") String apiKey,
            @Value("${binance.api.secret}") String secretKey) {
        
        // This service uses the MAINNET to fetch real live prices. 
        // It does not need the Testnet URL.
        this.client = new SpotClientImpl(apiKey, secretKey);
    }

    public String getTickerPrice(String symbol) {
        LinkedHashMap<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("symbol", symbol);
        
        // Fetch the 24hr ticker price
        return client.createMarket().ticker24H(parameters);
    }
}