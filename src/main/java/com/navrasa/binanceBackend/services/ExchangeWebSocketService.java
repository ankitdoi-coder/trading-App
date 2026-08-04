package com.navrasa.binanceBackend.services;

import com.navrasa.binanceBackend.services.ActiveTradeService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.navrasa.binanceBackend.config.LivePriceHandler;
import jakarta.annotation.PostConstruct;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URI;

@Service
public class ExchangeWebSocketService {

    private final LivePriceHandler livePriceHandler;
    private final ActiveTradeService activeTradeService;
    private final ObjectMapper mapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    public ExchangeWebSocketService(LivePriceHandler livePriceHandler, ActiveTradeService activeTradeService) {
        this.livePriceHandler = livePriceHandler;
        this.activeTradeService = activeTradeService;
    }

    @PostConstruct
    public void init() {
        connectBinance();
        connectBybit();
        connectKuCoin();
        connectCoinDCX();
    }

    private void connectBinance() {
        new Thread(() -> {
            try {
                // 🔥 THE FIX: Use Binance.US to bypass Render's US Datacenter Geo-block
                URI uri = new URI("wss://stream.binance.us:9443/ws/btcusdt@ticker");

                WebSocketClient client = new WebSocketClient(uri) {
                    @Override
                    public void onOpen(ServerHandshake handshake) {
                        System.out.println("✅Connected to Binance WebSocket");
                    }

                    @Override
                    public void onMessage(String message) {
                        try {
                            // 1. Broadcast to Angular
                            livePriceHandler.broadcast("{\"exchange\":\"BINANCE\", \"raw\":" + message + "}");

                            // 2. Feed price to Auto-Seller Engine
                            JsonNode data = mapper.readTree(message);
                            double currentPrice = data.path("c").asDouble();
                            activeTradeService.checkPriceAgainstLimits("BTCUSDT", currentPrice);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public void onClose(int code, String reason, boolean remote) {
                        System.out.println("Binance WS Closed: " + reason);
                    }

                    @Override
                    public void onError(Exception ex) {
                        // 🔥 Added error logging so it doesn't fail silently anymore!
                        System.err.println("Binance WS Error: " + ex.getMessage());
                    }
                };
                client.connect();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    // 2. BYBIT
    private void connectBybit() {
        new Thread(() -> {
            try {
                URI uri = new URI("wss://stream.bybit.com/v5/public/spot");
                WebSocketClient client = new WebSocketClient(uri) {
                    @Override
                    public void onOpen(ServerHandshake handshake) {
                        System.out.println(" ✅ Connected to Bybit websocket");
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
        }).start();
    }

    // 3. KUCOIN
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
                    Thread.sleep(1000);
                } catch (Exception e) {
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException ignored) {
                    }
                }
            }
        }).start();
    }

    // 4. COINDCX
    private void connectCoinDCX() {
        new Thread(() -> {
            System.out.println("✅ Started CoinDCX Service");
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
                    Thread.sleep(1000);
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