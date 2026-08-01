package com.navrasa.binanceBackend.controller;

import com.navrasa.binanceBackend.services.BinanceTradingService;
import com.navrasa.binanceBackend.services.BybitTradingService;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/trade")
@CrossOrigin(origins = "*")
public class TradeController {

    private final BybitTradingService bybitService;
    private final BinanceTradingService binanceService;

    public TradeController(BybitTradingService bybitService, BinanceTradingService binanceService) {
        this.bybitService = bybitService;
        this.binanceService = binanceService;
    }

    @PostMapping(value = "/execute", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> executeTrade(@RequestBody Map<String, Object> request) {
        try {
            String symbol = (String) request.getOrDefault("symbol", "BTCUSDT");
            String side = (String) request.getOrDefault("side", "BUY");
            
            // Note: Passed as string to prevent double decimal rounding issues
            String quantity = request.get("quantity").toString(); 

            // Execute on 
            String response = binanceService.executeOrder(symbol, side, quantity);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                Map.of("error", "Trade Rejected: " + e.getMessage())
            );
        }
    }
}