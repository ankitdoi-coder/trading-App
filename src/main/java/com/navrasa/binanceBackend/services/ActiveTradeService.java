package com.navrasa.binanceBackend.services;

import com.navrasa.binanceBackend.config.LivePriceHandler;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class ActiveTradeService {

    // Futures Trade Record with Unique Trade ID
    public static class FuturesTradeRecord {
        public String tradeId;
        public String symbol;
        public String direction; // "LONG" or "SHORT"
        public double entryPrice;
        public String quantity;
        public double stopLoss;
        public double takeProfit;

        public FuturesTradeRecord(String tradeId, String symbol, String direction, double entryPrice, String quantity,
                double stopLoss, double takeProfit) {
            this.tradeId = tradeId;
            this.symbol = symbol.toUpperCase();
            this.direction = direction.toUpperCase();
            this.entryPrice = entryPrice;
            this.quantity = quantity;
            this.stopLoss = stopLoss;
            this.takeProfit = takeProfit;
        }
    }

    // 🎯 MULTI-TRADE MAP: Key is "SYMBOL_DIRECTION" -> Value is a List of Active
    // Trades
    private final ConcurrentHashMap<String, List<FuturesTradeRecord>> activeFuturesTrades = new ConcurrentHashMap<>();

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

    // ==================== MULTI-TRADE FUTURES LOGIC ====================

    /**
     * Opens a new trade and adds it to the list for that symbol & direction.
     */
    public String openFuturesTrade(String symbol, String direction, double entryPrice, String quantity, double stopLoss,
            double takeProfit) {
        String tradeId = UUID.randomUUID().toString().substring(0, 8); // Unique 8-char ID
        String key = buildFuturesKey(symbol, direction);

        FuturesTradeRecord newTrade = new FuturesTradeRecord(tradeId, symbol, direction, entryPrice, quantity, stopLoss,
                takeProfit);

        // Compute or append to the thread-safe list
        activeFuturesTrades.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).add(newTrade);

        System.out.println(" Tracked New Futures Trade [" + tradeId + "] (" + direction.toUpperCase() + "): " + symbol
                + " | Entry: $" + entryPrice + " | Qty: " + quantity + " | SL: $" + stopLoss + " | TP: $" + takeProfit);

        return tradeId;
    }

    /**
     * Checks live price against ALL active trades across all directions.
     */
    public void checkFuturesPriceAgainstLimits(String symbol, double livePrice) {
        // 1. Check all active LONG trades
        List<FuturesTradeRecord> longTrades = activeFuturesTrades.get(buildFuturesKey(symbol, "LONG"));
        if (longTrades != null) {
            for (FuturesTradeRecord trade : longTrades) {
                if (livePrice <= trade.stopLoss) {
                    executeAutoFuturesClose(trade, livePrice, "FUTURES_LONG_STOP_LOSS");
                } else if (livePrice >= trade.takeProfit) {
                    executeAutoFuturesClose(trade, livePrice, "FUTURES_LONG_TAKE_PROFIT");
                }
            }
        }

        // 2. Check all active SHORT trades
        List<FuturesTradeRecord> shortTrades = activeFuturesTrades.get(buildFuturesKey(symbol, "SHORT"));
        if (shortTrades != null) {
            for (FuturesTradeRecord trade : shortTrades) {
                if (livePrice >= trade.stopLoss) { // Short SL triggers when price goes UP
                    executeAutoFuturesClose(trade, livePrice, "FUTURES_SHORT_STOP_LOSS");
                } else if (livePrice <= trade.takeProfit) { // Short TP triggers when price goes DOWN
                    executeAutoFuturesClose(trade, livePrice, "FUTURES_SHORT_TAKE_PROFIT");
                }
            }
        }
    }

    private void executeAutoFuturesClose(FuturesTradeRecord trade, double livePrice, String eventType) {
        System.out.println(" FUTURES LIMIT HIT! Auto-Closing Trade [" + trade.tradeId + "] (" + trade.direction
                + ") at $" + livePrice);
        try {
            String closingSide = "LONG".equalsIgnoreCase(trade.direction) ? "SELL" : "BUY";

            // Execute order on Binance for EXACT trade quantity
            binanceFuturesService.executeFuturesOrder(trade.symbol, trade.direction, closingSide, 1, trade.quantity);

            // Remove specific trade from active list
            removeTradeFromList(trade.symbol, trade.direction, trade.tradeId);

            String alertPayload = String.format(
                    "{\"exchange\":\"SYSTEM\", \"event\":\"%s\", \"symbol\":\"%s\", \"direction\":\"%s\", \"tradeId\":\"%s\", \"price\":%f}",
                    eventType, trade.symbol, trade.direction, trade.tradeId, livePrice);
            livePriceHandler.broadcast(alertPayload);

            System.out.println(" Futures Auto-Close Complete for Trade ID: " + trade.tradeId);
        } catch (Exception e) {
            System.err.println(" Futures Auto-Close Failed for Trade ID " + trade.tradeId + ": " + e.getMessage());
        }
    }

    /**
     * Manually close a specific trade by tradeId
     */
    public String manualFuturesCloseByTradeId(String symbol, String direction, String tradeId) {
        String key = buildFuturesKey(symbol, direction);
        List<FuturesTradeRecord> trades = activeFuturesTrades.get(key);

        if (trades != null) {
            for (FuturesTradeRecord trade : trades) {
                if (trade.tradeId.equalsIgnoreCase(tradeId)) {
                    String closingSide = "LONG".equalsIgnoreCase(trade.direction) ? "SELL" : "BUY";
                    String response = binanceFuturesService.executeFuturesOrder(trade.symbol, trade.direction,
                            closingSide, 1, trade.quantity);

                    removeTradeFromList(symbol, direction, tradeId);
                    return response;
                }
            }
        }
        return "{\"error\":\"Trade ID " + tradeId + " not found for symbol " + symbol + "\"}";
    }

    /**
     * Manually close all trades for a direction
     */
    public String manualFuturesCloseAll(String symbol, String direction) {
        String key = buildFuturesKey(symbol, direction);
        List<FuturesTradeRecord> trades = activeFuturesTrades.get(key);

        if (trades != null && !trades.isEmpty()) {
            StringBuilder responses = new StringBuilder("[");
            for (int i = 0; i < trades.size(); i++) {
                FuturesTradeRecord trade = trades.get(i);
                String closingSide = "LONG".equalsIgnoreCase(trade.direction) ? "SELL" : "BUY";
                String res = binanceFuturesService.executeFuturesOrder(trade.symbol, trade.direction, closingSide, 1,
                        trade.quantity);
                responses.append(res);
                if (i < trades.size() - 1)
                    responses.append(",");
            }
            responses.append("]");
            activeFuturesTrades.remove(key);
            return responses.toString();
        }
        return "{\"error\":\"No active " + direction + " futures trades found for " + symbol + "\"}";
    }

    private void removeTradeFromList(String symbol, String direction, String tradeId) {
        String key = buildFuturesKey(symbol, direction);
        List<FuturesTradeRecord> trades = activeFuturesTrades.get(key);
        if (trades != null) {
            trades.removeIf(t -> t.tradeId.equalsIgnoreCase(tradeId));
            if (trades.isEmpty()) {
                activeFuturesTrades.remove(key);
            }
        }
    }

    private String buildFuturesKey(String symbol, String direction) {
        return symbol.toUpperCase() + "_" + direction.toUpperCase();
    }

    
    /**
     * Calculates the total combined active futures trades.
     */
    public int getTotalActiveTradesCount() {
        int count = 0;
        for (List<FuturesTradeRecord> trades : activeFuturesTrades.values()) {
            count += trades.size();
        }
        return count;
    }
}