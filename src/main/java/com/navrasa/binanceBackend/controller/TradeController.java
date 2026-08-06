package com.navrasa.binanceBackend.controller;

import com.navrasa.binanceBackend.services.ActiveTradeService;
import com.navrasa.binanceBackend.services.BinanceFuturesTradingService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/trade")
@CrossOrigin(origins = { "http://localhost:4200", "https://tradingappfrontend-8vu1.onrender.com" })
public class TradeController {

    private final BinanceFuturesTradingService binanceFuturesService;
    private final ActiveTradeService activeTradeService;

    public TradeController(BinanceFuturesTradingService binanceFuturesService,
            ActiveTradeService activeTradeService) {
        this.binanceFuturesService = binanceFuturesService;
        this.activeTradeService = activeTradeService;
    }

    @PostMapping(value = "/execute", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> executeTrade(@RequestBody Map<String, Object> request) {
        try {
            // STRICT SECURITY: Reject if total combined trades >= 3
            if (activeTradeService.getTotalActiveTradesCount() >= 3) {
                return ResponseEntity.badRequest().body(Map.of("error", "Maximum active trade limit (3) reached."));
            }
            String symbol = (String) request.getOrDefault("symbol", "BTCUSDT");
            String direction = (String) request.getOrDefault("direction", "LONG");
            int leverage = Integer.parseInt(request.getOrDefault("leverage", 10).toString());
            String quantity = request.get("quantity").toString();

            String side = direction.equalsIgnoreCase("LONG") ? "BUY" : "SELL";

            // 1. Execute order on Binance Futures
            String response = binanceFuturesService.executeFuturesOrder(symbol, direction, side, leverage, quantity);

            // 2. Track trade in ActiveTradeService
            String tradeId = null;
            if (request.containsKey("stopLoss") && request.containsKey("takeProfit")
                    && request.containsKey("entryPrice")) {
                double stopLoss = Double.parseDouble(request.get("stopLoss").toString());
                double takeProfit = Double.parseDouble(request.get("takeProfit").toString());
                double entryPrice = Double.parseDouble(request.get("entryPrice").toString());

                tradeId = activeTradeService.openFuturesTrade(symbol, direction, entryPrice, quantity, stopLoss,
                        takeProfit);
            }

            return ResponseEntity.ok(Map.of(
                    "binanceResponse", response,
                    "tradeId", tradeId != null ? tradeId : "NONE"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Futures Trade Rejected: " + e.getMessage()));
        }
    }

    @CrossOrigin(origins = "http://localhost:4200")
    @PostMapping(value = "/close/{symbol}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> closeTrade(
            @PathVariable String symbol,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) String tradeId) {
        try {
            String response;
            if (tradeId != null && !tradeId.isEmpty() && direction != null) {
                // Close a SPECIFIC trade by tradeId
                response = activeTradeService.manualFuturesCloseByTradeId(symbol, direction, tradeId);
            } else if (direction != null && !direction.isEmpty()) {
                // Close ALL trades for direction
                response = activeTradeService.manualFuturesCloseAll(symbol.toUpperCase(), direction.toUpperCase());
            } else {
                response = "{\"error\":\"Direction or tradeId is required\"}";
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}