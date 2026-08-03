package com.navrasa.binanceBackend.services;

import com.navrasa.binanceBackend.config.LivePriceHandler;
import org.springframework.stereotype.Service;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ActiveTradeService {

    public static class TradeRecord {
        public double buyPrice;
        public String quantity;
        public double stopLoss; // lower Limit
        public double takeProfit; // Upper Limit

        public TradeRecord(double buyPrice, String quantity, double stopLoss, double takeProfit) {
            this.buyPrice = buyPrice;
            this.quantity = quantity;
            this.stopLoss = stopLoss;
            this.takeProfit = takeProfit;
        }
    }

    private final ConcurrentHashMap<String, TradeRecord> activeTrades = new ConcurrentHashMap<>();
    private final BinanceTradingService binanceService;
    private final LivePriceHandler livePriceHandler; // Inject the WebSocket handler

    // Update the constructor to include LivePriceHandler
    public ActiveTradeService(BinanceTradingService binanceService, LivePriceHandler livePriceHandler) {
        this.binanceService = binanceService;
        this.livePriceHandler = livePriceHandler;
    }

    public void openTrade(String symbol, double buyPrice, String quantity, double stopLoss, double takeProfit) {
        activeTrades.put(symbol, new TradeRecord(buyPrice, quantity, stopLoss, takeProfit));
        System.out.println(" Tracked Active Trade: " + symbol + " | Buy: $" + buyPrice + " | Stop Loss: $" + stopLoss
                + " | Take Profit: $" + takeProfit);
    }

    public void checkPriceAgainstLimits(String symbol, double livePrice) {
        TradeRecord trade = activeTrades.get(symbol);

        if (trade != null) {
            // Check Lower Limit (Stop Loss)
            if (livePrice <= trade.stopLoss) {
                executeAutoSell(symbol, trade.quantity, livePrice, "STOP_LOSS_TRIGGERED");
            }
            // Check Upper Limit (Take Profit)
            else if (livePrice >= trade.takeProfit) {
                executeAutoSell(symbol, trade.quantity, livePrice, "TAKE_PROFIT_TRIGGERED");
            }
        }
    }

    // Helper method to keep code clean
    private void executeAutoSell(String symbol, String quantity, double livePrice, String eventType) {
        System.out.println("🚨 LIMIT HIT! Executing Auto-Sell for " + symbol + " at $" + livePrice);
        try {
            binanceService.executeOrder(symbol, "SELL", quantity);
            activeTrades.remove(symbol);

            // Broadcast the specific event back to Angular
            String alertPayload = String.format(
                    "{\"exchange\":\"SYSTEM\", \"event\":\"%s\", \"symbol\":\"%s\", \"price\":%f}", eventType, symbol,
                    livePrice);
            livePriceHandler.broadcast(alertPayload);

            System.out.println("✅ Auto-Sell Execution Complete (" + eventType + ").");
        } catch (Exception e) {
            System.err.println("❌ Auto-Sell Failed: " + e.getMessage());
        }
    }

    public String manualClose(String symbol) {
        TradeRecord trade = activeTrades.get(symbol);
        if (trade != null) {
            String response = binanceService.executeOrder(symbol, "SELL", trade.quantity);
            activeTrades.remove(symbol);
            return response;
        }
        return binanceService.executeOrder(symbol, "SELL", "0.001");
    }
}