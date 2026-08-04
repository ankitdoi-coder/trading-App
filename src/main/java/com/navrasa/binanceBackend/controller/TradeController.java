// package com.navrasa.binanceBackend.controller;

// import com.navrasa.binanceBackend.services.ActiveTradeService;
// import com.navrasa.binanceBackend.services.BinanceTradingService;
// import com.navrasa.binanceBackend.services.BybitTradingService;
// import org.springframework.http.MediaType;
// import org.springframework.http.ResponseEntity;
// import org.springframework.web.bind.annotation.*;

// import java.util.Map;

// @RestController
// @RequestMapping("/api/trade")
// @CrossOrigin(origins = { "http://localhost:4200", "https://tradingappfrontend-8vu1.onrender.com" }) // Explicitly allow
//                                                                                                     // Angular origin
// public class TradeController {

//     private final BybitTradingService bybitService;
//     private final BinanceTradingService binanceService;
//     private final ActiveTradeService activeTradeService;

//     public TradeController(BybitTradingService bybitService, BinanceTradingService binanceService,
//             ActiveTradeService activeTradeService) {
//         this.bybitService = bybitService;
//         this.binanceService = binanceService;
//         this.activeTradeService = activeTradeService;
//     }

//     @PostMapping(value = "/execute", produces = MediaType.APPLICATION_JSON_VALUE)
//     public ResponseEntity<?> executeTrade(@RequestBody Map<String, Object> request) {
//         try {
//             String symbol = (String) request.getOrDefault("symbol", "BTCUSDT");
//             String side = (String) request.getOrDefault("side", "BUY");
//             String quantity = request.get("quantity").toString();

//             // Execute on Binance
//             String response = binanceService.executeOrder(symbol, side, quantity);

//             // Extract BOTH limits if it's a BUY order
//             if (side.equalsIgnoreCase("BUY") && request.containsKey("stopLoss") && request.containsKey("takeProfit")
//                     && request.containsKey("buyPrice")) {
//                 double stopLoss = Double.parseDouble(request.get("stopLoss").toString());
//                 double takeProfit = Double.parseDouble(request.get("takeProfit").toString());
//                 double buyPrice = Double.parseDouble(request.get("buyPrice").toString());

//                 activeTradeService.openTrade(symbol, buyPrice, quantity, stopLoss, takeProfit);
//             }

//             return ResponseEntity.ok(response);
//         } catch (Exception e) {
//             return ResponseEntity.badRequest().body(Map.of("error", "Trade Rejected: " + e.getMessage()));
//         }
//     }

//     // New Endpoint: Manual emergency close
//     // Added explicit @CrossOrigin here just to be absolutely safe
//     @CrossOrigin(origins = "http://localhost:4200")
//     @PostMapping(value = "/close/{symbol}", produces = MediaType.APPLICATION_JSON_VALUE)
//     public ResponseEntity<?> closeTrade(@PathVariable String symbol) {
//         try {
//             // This correctly calls the manualClose method which triggers a SELL
//             String response = activeTradeService.manualClose(symbol.toUpperCase());
//             return ResponseEntity.ok(response);
//         } catch (Exception e) {
//             return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
//         }
//     }
// }



package com.navrasa.binanceBackend.controller;

import com.navrasa.binanceBackend.services.ActiveTradeService;
import com.navrasa.binanceBackend.services.BinanceFuturesTradingService; // 🔥 Injected Futures Service
import com.navrasa.binanceBackend.services.BybitTradingService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/trade")
@CrossOrigin(origins = { "http://localhost:4200", "https://tradingappfrontend-8vu1.onrender.com" })
public class TradeController {

    private final BybitTradingService bybitService;
    private final BinanceFuturesTradingService binanceFuturesService; // 🔥 Futures Service
    private final ActiveTradeService activeTradeService;

    public TradeController(BybitTradingService bybitService, 
                           BinanceFuturesTradingService binanceFuturesService,
                           ActiveTradeService activeTradeService) {
        this.bybitService = bybitService;
        this.binanceFuturesService = binanceFuturesService;
        this.activeTradeService = activeTradeService;
    }

    @PostMapping(value = "/execute", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> executeTrade(@RequestBody Map<String, Object> request) {
        try {
            String symbol = (String) request.getOrDefault("symbol", "BTCUSDT");
            String direction = (String) request.getOrDefault("direction", "LONG");
            int leverage = Integer.parseInt(request.getOrDefault("leverage", 10).toString());
            String quantity = request.get("quantity").toString();

            // 🎯 1. Execute Order on BINANCE FUTURES TESTNET
            String response = binanceFuturesService.executeFuturesOrder(symbol, direction, leverage, quantity);

            // 🎯 2. Track Active Futures Position for SL/TP Auto-Closing
            if (request.containsKey("stopLoss") && request.containsKey("takeProfit") && request.containsKey("entryPrice")) {
                double stopLoss = Double.parseDouble(request.get("stopLoss").toString());
                double takeProfit = Double.parseDouble(request.get("takeProfit").toString());
                double entryPrice = Double.parseDouble(request.get("entryPrice").toString());

                activeTradeService.openFuturesTrade(symbol, direction, entryPrice, quantity, stopLoss, takeProfit);
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Futures Trade Rejected: " + e.getMessage()));
        }
    }

    @CrossOrigin(origins = "http://localhost:4200")
    @PostMapping(value = "/close/{symbol}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> closeTrade(@PathVariable String symbol) {
        try {
            // 🎯 3. Close Futures Position
            String response = activeTradeService.manualFuturesClose(symbol.toUpperCase());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}