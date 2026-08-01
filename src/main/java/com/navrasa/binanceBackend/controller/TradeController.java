package com.navrasa.binanceBackend.controller;

import com.navrasa.binanceBackend.services.BinanceTradingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/trade")
@CrossOrigin(origins = "*") // Allows Angular client requests from local and hosted URLs
public class TradeController {

    private final BinanceTradingService tradingService;

    public TradeController(BinanceTradingService tradingService) {
        this.tradingService = tradingService;
    }

    @PostMapping("/execute")
    public ResponseEntity<?> executeTrade(@RequestBody Map<String, Object> request) {
        try {
            String symbol = (String) request.getOrDefault("symbol", "BTCUSDT");
            String side = (String) request.getOrDefault("side", "BUY");
            double quantity = Double.parseDouble(request.get("quantity").toString());

            String binanceResponse = tradingService.executeOrder(symbol, side, quantity);
            return ResponseEntity.ok(binanceResponse);
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body("{\"error\":\"Trade failed: " + e.getMessage() + "\"}");
        }
    }
}