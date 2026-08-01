package com.navrasa.binanceBackend.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Service
public class BybitTradingService {

    private final String apiKey;
    private final String apiSecret;
    // Testnet URL for Bybit V5 API
    private final String bybitUrl = "https://api-testnet.bybit.com/v5/order/create"; 
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();

    public BybitTradingService(
            @Value("${bybit.api.key}") String apiKey,
            @Value("${bybit.api.secret}") String apiSecret) {
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
    }

    public String executeOrder(String symbol, String side, String quantity) throws Exception {
        // 1. Build the JSON Payload for a Spot Market Order
        Map<String, Object> payloadMap = new HashMap<>();
        payloadMap.put("category", "spot");
        payloadMap.put("symbol", symbol.toUpperCase()); // e.g., BTCUSDT
        payloadMap.put("side", side.substring(0, 1).toUpperCase() + side.substring(1).toLowerCase()); // "Buy" or "Sell"
        payloadMap.put("orderType", "Market");
        payloadMap.put("qty", quantity); 

        String jsonPayload = mapper.writeValueAsString(payloadMap);

        // 2. Generate Security Headers required by Bybit
        String timestamp = String.valueOf(Instant.now().toEpochMilli());
        String recvWindow = "5000";
        
        // The string to sign: timestamp + api_key + recv_window + jsonBodyString
        String rawData = timestamp + apiKey + recvWindow + jsonPayload;
        String signature = generateHmac256(apiSecret, rawData);

        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        headers.set("X-BAPI-API-KEY", apiKey);
        headers.set("X-BAPI-TIMESTAMP", timestamp);
        headers.set("X-BAPI-RECV-WINDOW", recvWindow);
        headers.set("X-BAPI-SIGN", signature);

        // 3. Send Request to Bybit
        HttpEntity<String> request = new HttpEntity<>(jsonPayload, headers);
        ResponseEntity<String> response = restTemplate.exchange(bybitUrl, HttpMethod.POST, request, String.class);

        return response.getBody();
    }

    // Standard HMAC_SHA256 Encryption method generating a Lowercase HEX string
    private String generateHmac256(String secret, String data) throws Exception {
        Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
        SecretKeySpec secret_key = new SecretKeySpec(secret.getBytes(), "HmacSHA256");
        sha256_HMAC.init(secret_key);
        byte[] hash = sha256_HMAC.doFinal(data.getBytes());
        
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }
}