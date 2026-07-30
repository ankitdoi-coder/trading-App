package com.navrasa.binanceBackend.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.navrasa.binanceBackend.config.LivePriceHandler;
import jakarta.annotation.PostConstruct;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.Map;

@Service
public class ExchangeWebSocketService {

    private final LivePriceHandler livePriceHandler;
    private final ObjectMapper mapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    public ExchangeWebSocketService(LivePriceHandler livePriceHandler) {
        this.livePriceHandler = livePriceHandler;
    }

    @PostConstruct
    public void init() {
        connectBinance();
        connectBybit();
        connectKuCoin();
        connectCoinDCX();
    }

    // 1. BINANCE
    private void connectBinance() {
        try {
            // Use standard HTTPS/WSS port 443 stream
            URI uri = new URI("wss://stream.binance.com:443/ws/btcusdt@ticker");

            WebSocketClient client = new WebSocketClient(uri) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    System.out.println("✅ Connected to Binance");
                }

                @Override
                public void onMessage(String message) {
                    livePriceHandler.broadcast("{\"exchange\":\"BINANCE\", \"raw\":" + message + "}");
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    System.out.println("Binance WS Closed: " + reason);
                }

                @Override
                public void onError(Exception ex) {
                    System.err.println("Binance Error: " + ex.getMessage());
                }
            };
            client.connect();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 2. BYBIT
    private void connectBybit() {
        try {
            URI uri = new URI("wss://stream.bybit.com/v5/public/spot");
            WebSocketClient client = new WebSocketClient(uri) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    System.out.println("✅ Connected to Bybit");
                    send("{\"op\":\"subscribe\",\"args\":[\"tickers.BTCUSDT\"]}");
                }

                @Override
                public void onMessage(String message) {
                    livePriceHandler.broadcast("{\"exchange\":\"BYBIT\", \"raw\":" + message + "}");
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                }

                @Override
                public void onError(Exception ex) {
                }
            };
            client.connect();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 3. KUCOIN (Polls official ticker endpoint every second)
    private void connectKuCoin() {
        new Thread(() -> {
            System.out.println("✅ Started KuCoin Service");
            while (true) {
                try {
                    String url = "https://api.kucoin.com/api/v1/market/stats?symbol=BTC-USDT";
                    String response = restTemplate.getForObject(url, String.class);
                    JsonNode root = mapper.readTree(response);
                    JsonNode data = root.path("data");

                    if (!data.isMissingNode()) {
                        livePriceHandler.broadcast("{\"exchange\":\"KUCOIN\", \"raw\":" + data.toString() + "}");
                    }
                    Thread.sleep(1000); // 1-second refresh interval
                } catch (Exception e) {
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException ignored) {
                    }
                }
            }
        }).start();
    }

    // 4. COINDCX (Polls public REST endpoint every second to emulate stream)
    private void connectCoinDCX() {
        new Thread(() -> {
            while (true) {
                try {
                    String url = "https://api.coindcx.com/exchange/ticker";
                    String response = restTemplate.getForObject(url, String.class);
                    JsonNode tickers = mapper.readTree(response);

                    for (JsonNode t : tickers) {
                        if ("BTCINR".equalsIgnoreCase(t.path("market").asText())) {
                            livePriceHandler.broadcast("{\"exchange\":\"COINDCX\", \"raw\":" + t.toString() + "}");
                            break;
                        }
                    }
                    Thread.sleep(1000); // 1-second refresh rate
                } catch (Exception e) {
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException ignored) {
                    }
                }
            }
        }).start();
    }
}