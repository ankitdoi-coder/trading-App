package com.navrasa.binanceBackend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final LivePriceHandler livePriceHandler;

    public WebSocketConfig(LivePriceHandler livePriceHandler) {
        this.livePriceHandler = livePriceHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Exposes ws://localhost:8080/ws-live-prices for Angular
        registry.addHandler(livePriceHandler, "/ws-live-prices")
                .setAllowedOrigins("*");
    }
}