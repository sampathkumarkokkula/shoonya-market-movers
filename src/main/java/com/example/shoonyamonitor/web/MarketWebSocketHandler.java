package com.example.shoonyamonitor.web;

import com.example.shoonyamonitor.service.MovementService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Native WebSocket endpoint the browser connects to. Keeps the set of open
 * sessions and pushes each new client an immediate snapshot so the dashboard
 * paints without waiting for the next scheduled broadcast.
 */
@Component
public class MarketWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(MarketWebSocketHandler.class);

    private final MovementService movementService;
    private final ObjectMapper mapper;
    private final Set<WebSocketSession> sessions = new CopyOnWriteArraySet<>();

    public MarketWebSocketHandler(MovementService movementService, ObjectMapper mapper) {
        this.movementService = movementService;
        this.mapper = mapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        try {
            session.sendMessage(new TextMessage(mapper.writeValueAsString(movementService.buildSnapshot())));
        } catch (IOException e) {
            log.debug("Failed to send initial snapshot: {}", e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
    }

    /** Sends the given payload to every currently open browser session. */
    public void broadcast(String payload) {
        TextMessage message = new TextMessage(payload);
        for (WebSocketSession s : sessions) {
            if (!s.isOpen()) {
                sessions.remove(s);
                continue;
            }
            try {
                synchronized (s) {
                    s.sendMessage(message);
                }
            } catch (IOException e) {
                log.debug("Dropping session after send failure: {}", e.getMessage());
                sessions.remove(s);
            }
        }
    }

    public int sessionCount() {
        return sessions.size();
    }
}
