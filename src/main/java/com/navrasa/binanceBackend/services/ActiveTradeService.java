package com.navrasa.binanceBackend.services;

import com.navrasa.binanceBackend.config.LivePriceHandler;
import org.springframework.stereotype.Service;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ActiveTradeService {

    // ==================== INNER RECORD CLASSES ====================

    // Spot Trade State
    public static class TradeRecord {
        public double buyPrice;
        public String quantity;
        public double stopLoss;  // Lower limit
        public double takeProfit; // Upper limit

        public TradeRecord(double buyPrice, String quantity, double stopLoss, double takeProfit) {
            this.buyPrice = buyPrice;
            this.quantity = quantity;
            this.stopLoss = stopLoss;
            this.takeProfit = takeProfit;
        }
    }

    // Futures Trade State (Direction-Aware)
    public static class FuturesTradeRecord {
        public String direction; // "LONG" or "SHORT"
        public double entryPrice;
        public String quantity;
        public double stopLoss;
        public double takeProfit;
        
        public FuturesTradeRecord(String direction, double entryPrice, String quantity, double stopLoss, double takeProfit) {
            this.direction = direction;
            this.entryPrice = entryPrice;
            this.quantity = quantity;
            this.stopLoss = stopLoss;
            this.takeProfit = takeProfit;
        }
    }

    // ==================== STATE MAPS & INJECTIONS ====================

    private final ConcurrentHashMap<String, TradeRecord> activeTrades = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, FuturesTradeRecord> activeFuturesTrades = new ConcurrentHashMap<>();

    private final BinanceTradingService binanceService;
    private final BinanceFuturesTradingService binanceFuturesService;
    private final LivePriceHandler livePriceHandler;

    public ActiveTradeService(BinanceTradingService binanceService, 
                              BinanceFuturesTradingService binanceFuturesService, 
                              LivePriceHandler livePriceHandler) {
        this.binanceService = binanceService;
        this.binanceFuturesService = binanceFuturesService;
        this.livePriceHandler = livePriceHandler;
    }

    // ==================== SPOT TRADING LOGIC ====================

    public void openTrade(String symbol, double buyPrice, String quantity, double stopLoss, double takeProfit) {
        activeTrades.put(symbol, new TradeRecord(buyPrice, quantity, stopLoss, takeProfit));
        System.out.println(" Tracked Active Spot Trade: " + symbol + " | Buy: $" + buyPrice 
                + " | Stop Loss: $" + stopLoss + " | Take Profit: $" + takeProfit);
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

    private void executeAutoSell(String symbol, String quantity, double livePrice, String eventType) {
        System.out.println("🚨 LIMIT HIT! Executing Auto-Sell (Spot) for " + symbol + " at $" + livePrice);
        try {
            binanceService.executeOrder(symbol, "SELL", quantity);
            activeTrades.remove(symbol);

            // Broadcast alert back to Angular via WebSocket
            String alertPayload = String.format(
                    "{\"exchange\":\"SYSTEM\", \"event\":\"%s\", \"symbol\":\"%s\", \"price\":%f}", 
                    eventType, symbol, livePrice);
            livePriceHandler.broadcast(alertPayload);

            System.out.println("✅ Spot Auto-Sell Complete (" + eventType + ").");
        } catch (Exception e) {
            System.err.println("❌ Spot Auto-Sell Failed: " + e.getMessage());
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

    // ==================== FUTURES TRADING LOGIC ====================

    public void openFuturesTrade(String symbol, String direction, double entryPrice, String quantity, double stopLoss, double takeProfit) {
        activeFuturesTrades.put(symbol, new FuturesTradeRecord(direction.toUpperCase(), entryPrice, quantity, stopLoss, takeProfit));
        System.out.println(" Tracked Active Futures Trade (" + direction + "): " + symbol + " | Entry: $" + entryPrice 
                + " | Stop Loss: $" + stopLoss + " | Take Profit: $" + takeProfit);
    }

    public void checkFuturesPriceAgainstLimits(String symbol, double livePrice) {
        FuturesTradeRecord trade = activeFuturesTrades.get(symbol);
        if (trade == null) return;

        if ("LONG".equalsIgnoreCase(trade.direction)) {
            // LONG POSITION: Stop Loss is below entry, Take Profit is above entry
            if (livePrice <= trade.stopLoss) {
                executeAutoFuturesClose(symbol, trade, livePrice, "FUTURES_LONG_STOP_LOSS");
            } else if (livePrice >= trade.takeProfit) {
                executeAutoFuturesClose(symbol, trade, livePrice, "FUTURES_LONG_TAKE_PROFIT");
            }
        } else if ("SHORT".equalsIgnoreCase(trade.direction)) {
            // SHORT POSITION: Stop Loss is ABOVE entry, Take Profit is BELOW entry
            if (livePrice >= trade.stopLoss) {
                executeAutoFuturesClose(symbol, trade, livePrice, "FUTURES_SHORT_STOP_LOSS");
            } else if (livePrice <= trade.takeProfit) {
                executeAutoFuturesClose(symbol, trade, livePrice, "FUTURES_SHORT_TAKE_PROFIT");
            }
        }
    }

    private void executeAutoFuturesClose(String symbol, FuturesTradeRecord trade, double livePrice, String eventType) {
        System.out.println("🚨 FUTURES LIMIT HIT! Auto-Closing " + trade.direction + " for " + symbol + " at $" + livePrice);
        try {
            // Closing a LONG requires a SELL order.
            // Closing a SHORT requires a BUY order.
            String closingSide = "LONG".equalsIgnoreCase(trade.direction) ? "SELL" : "BUY";

            binanceFuturesService.executeFuturesOrder(symbol, closingSide, 1, trade.quantity);
            activeFuturesTrades.remove(symbol);

            String alertPayload = String.format(
                    "{\"exchange\":\"SYSTEM\", \"event\":\"%s\", \"symbol\":\"%s\", \"price\":%f}", 
                    eventType, symbol, livePrice);
            livePriceHandler.broadcast(alertPayload);

            System.out.println("✅ Futures Auto-Close Complete (" + eventType + ").");
        } catch (Exception e) {
            System.err.println("❌ Futures Auto-Close Failed: " + e.getMessage());
        }
    }

    public String manualFuturesClose(String symbol) {
        FuturesTradeRecord trade = activeFuturesTrades.get(symbol);
        if (trade != null) {
            String closingSide = "LONG".equalsIgnoreCase(trade.direction) ? "SELL" : "BUY";
            String response = binanceFuturesService.executeFuturesOrder(symbol, closingSide, 1, trade.quantity);
            activeFuturesTrades.remove(symbol);
            return response;
        }
        return "{\"error\":\"No active futures position found for symbol " + symbol + "\"}";
    }
}