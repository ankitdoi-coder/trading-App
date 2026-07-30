package com.navrasa.binanceBackend.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import org.springframework.web.bind.annotation.RestController;

import com.navrasa.binanceBackend.services.BinanceService;

import org.springframework.web.bind.annotation.CrossOrigin;

@RestController
@CrossOrigin(origins = "http://localhost:4200") // Allow Angular to call this API
public class BinanceController {

    private final BinanceService binanceService;

    public BinanceController(BinanceService binanceService) {
        this.binanceService = binanceService;
    }

    @GetMapping("/api/price/{symbol}")
    public String getPrice(@PathVariable String symbol) {
        return binanceService.getTickerPrice(symbol);
    }
}