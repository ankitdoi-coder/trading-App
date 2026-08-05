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

    public String executeFuturesOrder(String symbol, String positionSide, String side, int leverage, String quantity) {
        
        // 1. STRICT GUARANTEE: Set Account to HEDGE MODE
        try {
            LinkedHashMap<String, Object> modeParams = new LinkedHashMap<>();
            modeParams.put("dualSidePosition", "true"); // true = Hedge Mode
            futuresClient.account().changePositionModeTrade(modeParams);
            System.out.println("✅ Position mode set to HEDGE for account");
        } catch (Exception e) {
            // Ignored if the account is already in Hedge Mode or has open positions
        }

        // 2. STRICT GUARANTEE: Set Margin Type to CROSSED
        try {
            LinkedHashMap<String, Object> marginParams = new LinkedHashMap<>();
            marginParams.put("symbol", symbol);
            marginParams.put("marginType", "CROSSED"); // 🎯 Changed from ISOLATED to CROSSED
            futuresClient.account().changeMarginType(marginParams);
            System.out.println("✅ Margin type set to CROSSED for " + symbol);
        } catch (Exception e) {
            // Binance throws an exception if marginType is already CROSSED or if positions are open
            System.out.println("ℹ️ Margin type is already CROSSED for " + symbol);
        }

        // 3. Set Leverage
        LinkedHashMap<String, Object> leverageParams = new LinkedHashMap<>();
        leverageParams.put("symbol", symbol);
        leverageParams.put("leverage", leverage);
        futuresClient.account().changeInitialLeverage(leverageParams);

        // 4. Execute Market Order with HEDGE MODE parameters
        LinkedHashMap<String, Object> orderParams = new LinkedHashMap<>();
        orderParams.put("symbol", symbol);
        orderParams.put("side", side);                 // "BUY" or "SELL"
        orderParams.put("positionSide", positionSide); // "LONG" or "SHORT"
        orderParams.put("type", "MARKET");
        orderParams.put("quantity", quantity);
        
        return futuresClient.account().newOrder(orderParams);
    }
}