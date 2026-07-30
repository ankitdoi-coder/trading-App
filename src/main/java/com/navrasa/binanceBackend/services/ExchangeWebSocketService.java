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

   private void connectBinance() {
    new Thread(() -> {
        System.out.println("✅ Started Binance REST Polling Service");
        while (true) {
            try {
                String url = "https://data-api.binance.vision/api/v3/ticker/24hr?symbol=BTCUSDT";
                String response = restTemplate.getForObject(url, String.class);
                JsonNode data = mapper.readTree(response);

                if (!data.isMissingNode()) {
                    String jsonPayload = String.format(
                        "{\"s\":\"%s\",\"c\":\"%s\",\"P\":\"%s\",\"h\":\"%s\",\"l\":\"%s\"}",
                        data.path("symbol").asText(),
                        data.path("lastPrice").asText(),
                        data.path("priceChangePercent").asText(),
                        data.path("highPrice").asText(),
                        data.path("lowPrice").asText()
                    );
                    livePriceHandler.broadcast("{\"exchange\":\"BINANCE\", \"raw\":" + jsonPayload + "}");
                }
                Thread.sleep(1000);
            } catch (Exception e) {
                try { Thread.sleep(3000); } catch (InterruptedException ignored) {}
            }
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
                        System.out.println("✅ Connected to Bybit");
                        send("{\"op\":\"subscribe\",\"args\":[\"tickers.BTCUSDT\"]}");
                    }

                    @Override
                    public void onMessage(String message) {
                        livePriceHandler.broadcast("{\"exchange\":\"BYBIT\", \"raw\":" + message + "}");
                    }

                    @Override
                    public void onClose(int code, String reason, boolean remote) {}

                    @Override
                    public void onError(Exception ex) {}
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
                    try { Thread.sleep(3000); } catch (InterruptedException ignored) {}
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
                    try { Thread.sleep(3000); } catch (InterruptedException ignored) {}
                }
            }
        }).start();
    }
}