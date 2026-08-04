package com.navrasa.binanceBackend.services;

import com.binance.connector.futures.client.impl.UMFuturesClientImpl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;

@Service
public class BinanceFuturesTradingService {

    private final UMFuturesClientImpl futuresClient;

    public BinanceFuturesTradingService(
            @Value("${binance.api.key}") String apiKey,
            @Value("${binance.api.secret}") String secretKey) {
        
        // Use the Futures Testnet for safe development
        this.futuresClient = new UMFuturesClientImpl(apiKey, secretKey, "https://testnet.binancefuture.com");
    }

    public String executeFuturesOrder(String symbol, String direction, int leverage, String quantity) {
        // 1. Set Leverage First
        LinkedHashMap<String, Object> leverageParams = new LinkedHashMap<>();
        leverageParams.put("symbol", symbol);
        leverageParams.put("leverage", leverage);
        futuresClient.account().changeInitialLeverage(leverageParams);

        // 2. Determine Binance Side based on Direction
        String side = direction.equalsIgnoreCase("LONG") ? "BUY" : "SELL";

        // 3. Execute Market Order
        LinkedHashMap<String, Object> orderParams = new LinkedHashMap<>();
        orderParams.put("symbol", symbol);
        orderParams.put("side", side);
        orderParams.put("type", "MARKET");
        orderParams.put("quantity", quantity);
        
        return futuresClient.account().newOrder(orderParams);
    }
}