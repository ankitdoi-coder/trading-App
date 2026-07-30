package com.navrasa.binanceBackend.config;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class LivePriceHandler extends TextWebSocketHandler {

    private final CopyOnWriteArrayList<WebSocketSession> sessions = new CopyOnWriteArrayList<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
    }

    public void broadcast(String payload) {
        TextMessage message = new TextMessage(payload);
        
        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                // Synchronize on individual session to ensure thread safety
                synchronized (session) {
                    try {
                        if (session.isOpen()) {
                            session.sendMessage(message);
                        }
                    } catch (IOException e) {
                        // Remove invalid session if client disconnected
                        sessions.remove(session);
                    } catch (IllegalStateException e) {
                        // Suppress state exception if frame is concurrently writing
                    }
                }
            }
        }
    }
}