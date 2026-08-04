package com.navrasa.binanceBackend.services;

import com.binance.connector.futures.client.impl.UMFuturesClientImpl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;

@Service
public class BinanceFuturesTradingService {

    private final UMFuturesClientImpl futuresClient;

    public BinanceFuturesTradingService(
            @Value("${binance.futures.api.key}") String apiKey,
            @Value("${binance.futures.api.secret}") String secretKey) {

        // Use the Futures Testnet for safe development
        this.futuresClient = new UMFuturesClientImpl(apiKey, secretKey, "https://testnet.binancefuture.com");
    }

    public String executeFuturesOrder(String symbol, String direction, int leverage, String quantity) {

        // 1. STRICT GUARANTEE: Set Margin Type to ISOLATED
        try {
            LinkedHashMap<String, Object> marginParams = new LinkedHashMap<>();
            marginParams.put("symbol", symbol);
            marginParams.put("marginType", "ISOLATED");
            futuresClient.account().changeMarginType(marginParams);
            System.out.println("✅ Margin type set to ISOLATED for " + symbol);
        } catch (Exception e) {
            // Binance throws an error if it's already set to ISOLATED.
            // We catch and ignore it so the trade can continue.
            System.out.println("ℹ️ Margin type is already ISOLATED for " + symbol);
        }

        // 2. Set Leverage
        LinkedHashMap<String, Object> leverageParams = new LinkedHashMap<>();
        leverageParams.put("symbol", symbol);
        leverageParams.put("leverage", leverage);
        futuresClient.account().changeInitialLeverage(leverageParams);

        // 3. Determine Binance Side based on Direction
        String side = direction.equalsIgnoreCase("LONG") ? "BUY" : "SELL";

        // 4. Execute Market Order
        LinkedHashMap<String, Object> orderParams = new LinkedHashMap<>();
        orderParams.put("symbol", symbol);
        orderParams.put("side", side);
        orderParams.put("type", "MARKET");
        orderParams.put("quantity", quantity);

        return futuresClient.account().newOrder(orderParams);
    }
}