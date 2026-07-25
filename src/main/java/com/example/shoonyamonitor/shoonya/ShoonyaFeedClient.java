package com.example.shoonyamonitor.shoonya;

import com.example.shoonyamonitor.config.ShoonyaProperties;
import com.example.shoonyamonitor.model.Instrument;
import com.example.shoonyamonitor.store.InstrumentRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Live market-data client for the Shoonya (Noren) WebSocket.
 *
 * <p>Only created when {@code shoonya.mock=false}. It logs in via
 * {@link ShoonyaAuthService}, opens the feed, subscribes to all configured
 * instruments and forwards every touchline update to {@link FeedIngest}.
 * A heartbeat is sent every 3 seconds and the connection reconnects
 * automatically after a drop.</p>
 */
@Component
@ConditionalOnProperty(name = "shoonya.mock", havingValue = "false")
public class ShoonyaFeedClient extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ShoonyaFeedClient.class);

    private final ShoonyaProperties props;
    private final ShoonyaAuthService authService;
    private final InstrumentRegistry registry;
    private final FeedIngest ingest;
    private final FeedStatus status;
    private final ObjectMapper mapper = new ObjectMapper();

    private final StandardWebSocketClient client = new StandardWebSocketClient();
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "shoonya-feed");
                t.setDaemon(true);
                return t;
            });

    private volatile WebSocketSession session;
    private volatile String susertoken;
    private volatile boolean shuttingDown;
    private ScheduledFuture<?> heartbeat;

    public ShoonyaFeedClient(ShoonyaProperties props,
                             ShoonyaAuthService authService,
                             InstrumentRegistry registry,
                             FeedIngest ingest,
                             FeedStatus status) {
        this.props = props;
        this.authService = authService;
        this.registry = registry;
        this.ingest = ingest;
        this.status = status;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        status.setMode("LIVE");
        connect();
    }

    private void connect() {
        if (shuttingDown) {
            return;
        }
        try {
            log.info("Authenticating with Shoonya...");
            this.susertoken = authService.login();
            log.info("Opening market-data WebSocket at {}", props.getWsUrl());
            client.execute(this, props.getWsUrl())
                    .exceptionally(ex -> {
                        log.error("WebSocket connect failed: {}", ex.getMessage());
                        scheduleReconnect();
                        return null;
                    });
        } catch (Exception e) {
            log.error("Startup failed ({}). Retrying in 10s.", e.getMessage());
            scheduleReconnect();
        }
    }

    private void scheduleReconnect() {
        if (shuttingDown) {
            return;
        }
        status.setConnected(false);
        scheduler.schedule(this::connect, 10, TimeUnit.SECONDS);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        this.session = session;
        Map<String, String> connect = new LinkedHashMap<>();
        connect.put("t", "c");
        connect.put("uid", props.getUserId());
        connect.put("actid", props.getUserId());
        connect.put("source", "API");
        connect.put("susertoken", susertoken);
        send(mapper.writeValueAsString(connect));
        log.info("Connect frame sent, waiting for acknowledgement");
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonNode node = mapper.readTree(message.getPayload());
        String t = node.path("t").asText("");
        switch (t) {
            case "ck" -> {
                if ("OK".equalsIgnoreCase(node.path("s").asText(""))) {
                    log.info("Feed connected. Subscribing to {} instruments.", registry.all().size());
                    status.setConnected(true);
                    subscribeAll();
                    startHeartbeat();
                } else {
                    log.error("Connect acknowledgement not OK: {}", message.getPayload());
                }
            }
            case "tk", "tf" -> ingest.applyTouchline(node);
            default -> {
                // tk/tf/ck handled; ignore order feeds, dpr, etc. for this info-only app
            }
        }
    }

    private void subscribeAll() throws Exception {
        String keys = registry.all().stream()
                .map(Instrument::key)
                .collect(Collectors.joining("#"));
        Map<String, String> sub = new LinkedHashMap<>();
        sub.put("t", "t"); // touchline
        sub.put("k", keys);
        send(mapper.writeValueAsString(sub));
    }

    private void startHeartbeat() {
        if (heartbeat != null) {
            heartbeat.cancel(false);
        }
        heartbeat = scheduler.scheduleAtFixedRate(() -> {
            try {
                send("{\"t\":\"h\"}");
            } catch (Exception e) {
                log.debug("Heartbeat send failed: {}", e.getMessage());
            }
        }, 3, 3, TimeUnit.SECONDS);
    }

    private synchronized void send(String payload) throws Exception {
        WebSocketSession s = this.session;
        if (s != null && s.isOpen()) {
            s.sendMessage(new TextMessage(payload));
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("Feed transport error: {}", exception.getMessage());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) {
        log.warn("Feed connection closed ({}). Reconnecting.", closeStatus);
        status.setConnected(false);
        if (heartbeat != null) {
            heartbeat.cancel(false);
        }
        scheduleReconnect();
    }

    @PreDestroy
    public void shutdown() {
        shuttingDown = true;
        if (heartbeat != null) {
            heartbeat.cancel(false);
        }
        scheduler.shutdownNow();
        try {
            if (session != null && session.isOpen()) {
                session.close();
            }
        } catch (Exception ignored) {
            // best effort on shutdown
        }
    }
}
