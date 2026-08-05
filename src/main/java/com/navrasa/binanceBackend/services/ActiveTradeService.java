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
        public double stopLoss; // Lower limit
        public double takeProfit; // Upper limit

        public TradeRecord(double buyPrice, String quantity, double stopLoss, double takeProfit) {
            this.buyPrice = buyPrice;
            this.quantity = quantity;
            this.stopLoss = stopLoss;
            this.takeProfit = takeProfit;
        }
    }

    // Futures Trade State (Hedge Mode & Direction-Aware)
    public static class FuturesTradeRecord {
        public String symbol;
        public String direction; // "LONG" or "SHORT"
        public double entryPrice;
        public String quantity;
        public double stopLoss;
        public double takeProfit;

        public FuturesTradeRecord(String symbol, String direction, double entryPrice, String quantity, double stopLoss,
                double takeProfit) {
            this.symbol = symbol;
            this.direction = direction;
            this.entryPrice = entryPrice;
            this.quantity = quantity;
            this.stopLoss = stopLoss;
            this.takeProfit = takeProfit;
        }
    }

    // ==================== STATE MAPS & INJECTIONS ====================
    private final ConcurrentHashMap<String, TradeRecord> activeTrades = new ConcurrentHashMap<>();

    // 🎯 HEDGE MODE FIX: Map key is "SYMBOL_DIRECTION" (e.g., "BTCUSDT_LONG" &
    // "BTCUSDT_SHORT")
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
        activeTrades.put(symbol.toUpperCase(), new TradeRecord(buyPrice, quantity, stopLoss, takeProfit));
        System.out.println(" Tracked Active Spot Trade: " + symbol + " | Buy: $" + buyPrice
                + " | Stop Loss: $" + stopLoss + " | Take Profit: $" + takeProfit);
    }

    public void checkPriceAgainstLimits(String symbol, double livePrice) {
        TradeRecord trade = activeTrades.get(symbol.toUpperCase());
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

        // Feed price ticks into Futures positions as well
        checkFuturesPriceAgainstLimits(symbol, livePrice);
    }

    private void executeAutoSell(String symbol, String quantity, double livePrice, String eventType) {
        System.out.println("  LIMIT HIT! Executing Auto-Sell (Spot) for " + symbol + " at $" + livePrice);
        try {
            binanceService.executeOrder(symbol, "SELL", quantity);
            activeTrades.remove(symbol.toUpperCase());

            // Broadcast alert back to Angular via WebSocket
            String alertPayload = String.format(
                    "{\"exchange\":\"SYSTEM\", \"event\":\"%s\", \"symbol\":\"%s\", \"price\":%f}",
                    eventType, symbol, livePrice);
            livePriceHandler.broadcast(alertPayload);
            System.out.println("  Spot Auto-Sell Complete (" + eventType + ").");
        } catch (Exception e) {
            System.err.println("  Spot Auto-Sell Failed: " + e.getMessage());
        }
    }

    public String manualClose(String symbol) {
        TradeRecord trade = activeTrades.get(symbol.toUpperCase());
        if (trade != null) {
            String response = binanceService.executeOrder(symbol, "SELL", trade.quantity);
            activeTrades.remove(symbol.toUpperCase());
            return response;
        }
        return binanceService.executeOrder(symbol, "SELL", "0.001");
    }

    // ==================== FUTURES TRADING LOGIC (HEDGE MODE & CROSSED MARGIN)
    // ====================

    /**
     * Tracks an active Futures position in Hedge Mode.
     * Uses composite key "SYMBOL_DIRECTION" so both LONG and SHORT can exist
     * simultaneously.
     */
    public void openFuturesTrade(String symbol, String direction, double entryPrice, String quantity, double stopLoss,
            double takeProfit) {
        String key = buildFuturesKey(symbol, direction);
        activeFuturesTrades.put(key, new FuturesTradeRecord(symbol.toUpperCase(), direction.toUpperCase(), entryPrice,
                quantity, stopLoss, takeProfit));
        System.out.println(" Tracked Active Futures Trade (" + direction.toUpperCase() + "): " + symbol
                + " | Key: " + key + " | Entry: $" + entryPrice
                + " | Stop Loss: $" + stopLoss + " | Take Profit: $" + takeProfit);
    }

    /**
     * Checks live price against both LONG and SHORT positions independently for a
     * symbol.
     */
    public void checkFuturesPriceAgainstLimits(String symbol, double livePrice) {
        // 1. Check active LONG position
        FuturesTradeRecord longTrade = activeFuturesTrades.get(buildFuturesKey(symbol, "LONG"));
        if (longTrade != null) {
            if (livePrice <= longTrade.stopLoss) {
                executeAutoFuturesClose(longTrade, livePrice, "FUTURES_LONG_STOP_LOSS");
            } else if (livePrice >= longTrade.takeProfit) {
                executeAutoFuturesClose(longTrade, livePrice, "FUTURES_LONG_TAKE_PROFIT");
            }
        }

        // 2. Check active SHORT position
        FuturesTradeRecord shortTrade = activeFuturesTrades.get(buildFuturesKey(symbol, "SHORT"));
        if (shortTrade != null) {
            if (livePrice >= shortTrade.stopLoss) { // Short SL triggers when price goes UP
                executeAutoFuturesClose(shortTrade, livePrice, "FUTURES_SHORT_STOP_LOSS");
            } else if (livePrice <= shortTrade.takeProfit) { // Short TP triggers when price goes DOWN
                executeAutoFuturesClose(shortTrade, livePrice, "FUTURES_SHORT_TAKE_PROFIT");
            }
        }
    }

    private void executeAutoFuturesClose(FuturesTradeRecord trade, double livePrice, String eventType) {
        System.out.println(
                "  FUTURES LIMIT HIT! Auto-Closing " + trade.direction + " for " + trade.symbol + " at $" + livePrice);
        try {
            // In Hedge Mode:
            // Closing a LONG position requires positionSide="LONG" and side="SELL"
            // Closing a SHORT position requires positionSide="SHORT" and side="BUY"
            String closingSide = "LONG".equalsIgnoreCase(trade.direction) ? "SELL" : "BUY";

            binanceFuturesService.executeFuturesOrder(trade.symbol, trade.direction, closingSide, 1, trade.quantity);

            String key = buildFuturesKey(trade.symbol, trade.direction);
            activeFuturesTrades.remove(key);

            String alertPayload = String.format(
                    "{\"exchange\":\"SYSTEM\", \"event\":\"%s\", \"symbol\":\"%s\", \"direction\":\"%s\", \"price\":%f}",
                    eventType, trade.symbol, trade.direction, livePrice);
            livePriceHandler.broadcast(alertPayload);
            System.out.println("  Futures Auto-Close Complete (" + eventType + ").");
        } catch (Exception e) {
            System.err.println("  Futures Auto-Close Failed: " + e.getMessage());
        }
    }

    /**
     * Manual close with explicit direction (Hedge Mode compatible)
     */
    public String manualFuturesClose(String symbol, String direction) {
        String key = buildFuturesKey(symbol, direction);
        FuturesTradeRecord trade = activeFuturesTrades.get(key);
        if (trade != null) {
            String closingSide = "LONG".equalsIgnoreCase(trade.direction) ? "SELL" : "BUY";
            String response = binanceFuturesService.executeFuturesOrder(trade.symbol, trade.direction, closingSide, 1,
                    trade.quantity);
            activeFuturesTrades.remove(key);
            return response;
        }
        return "{\"error\":\"No active " + direction + " futures position found for symbol " + symbol + "\"}";
    }

    /**
     * Fallback manual close when direction is not specified.
     * Formats output as valid JSON to prevent Angular parsing errors.
     */
    public String manualFuturesClose(String symbol) {
        String longKey = buildFuturesKey(symbol, "LONG");
        String shortKey = buildFuturesKey(symbol, "SHORT");

        boolean found = false;
        StringBuilder jsonBuilder = new StringBuilder("{");

        if (activeFuturesTrades.containsKey(longKey)) {
            jsonBuilder.append("\"longClose\":").append(manualFuturesClose(symbol, "LONG"));
            found = true;
        }
        if (activeFuturesTrades.containsKey(shortKey)) {
            if (found)
                jsonBuilder.append(",");
            jsonBuilder.append("\"shortClose\":").append(manualFuturesClose(symbol, "SHORT"));
            found = true;
        }

        if (!found) {
            return "{\"error\":\"No active futures positions found for symbol " + symbol + "\"}";
        }
        jsonBuilder.append("}");
        return jsonBuilder.toString();
    }

    private String buildFuturesKey(String symbol, String direction) {
        return symbol.toUpperCase() + "_" + direction.toUpperCase();
    }
}