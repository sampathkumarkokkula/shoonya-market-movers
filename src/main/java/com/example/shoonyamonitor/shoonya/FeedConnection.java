package com.example.shoonyamonitor.shoonya;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

/**
 * A single Shoonya (Noren) OAuth feed connection responsible for one batch of
 * instrument keys. It sends the OAuth connect frame, subscribes its batch on
 * acknowledgement, heartbeats every 3 seconds, forwards touchline ticks to the
 * shared {@link FeedIngest}, and reconnects (re-subscribing the same batch) if
 * the socket drops.
 *
 * <p>Instances are created and owned by {@link ShoonyaFeedClient}; they are not
 * Spring beans.</p>
 */
public class FeedConnection extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(FeedConnection.class);

    /** One-time confirmation logged across all connections when data arrives. */
    private static final AtomicBoolean FIRST_TICK_LOGGED = new AtomicBoolean(false);

    private final int id;
    private final String wsUrl;
    private final String uid;
    private final String actid;
    private final String accessToken;
    private final List<String> keys;
    private final FeedIngest ingest;
    private final ObjectMapper mapper;
    private final StandardWebSocketClient client;
    private final ScheduledExecutorService scheduler;
    /** Callback invoked with (connectionId, connected?) on state transitions. */
    private final BiConsumer<Integer, Boolean> onStateChange;

    private volatile WebSocketSession session;
    private volatile boolean shuttingDown;
    private volatile boolean connectedReported;
    private ScheduledFuture<?> heartbeat;

    public FeedConnection(int id, String wsUrl, String uid, String actid, String accessToken,
                          List<String> keys, FeedIngest ingest, ObjectMapper mapper,
                          StandardWebSocketClient client, BiConsumer<Integer, Boolean> onStateChange) {
        this.id = id;
        this.wsUrl = wsUrl;
        this.uid = uid;
        this.actid = actid;
        this.accessToken = accessToken;
        this.keys = keys;
        this.ingest = ingest;
        this.mapper = mapper;
        this.client = client;
        this.onStateChange = onStateChange;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "shoonya-feed-" + id);
            t.setDaemon(true);
            return t;
        });
    }

    /** Opens the WebSocket; retries after a delay on failure. */
    public void connect() {
        if (shuttingDown) {
            return;
        }
        try {
            log.info("Feed connection {} opening ({} instruments) at {}", id, keys.size(), wsUrl);
            client.execute(this, wsUrl).exceptionally(ex -> {
                log.error("Feed connection {} connect failed: {}", id, ex.getMessage());
                scheduleReconnect();
                return null;
            });
        } catch (Exception e) {
            log.error("Feed connection {} startup failed ({}). Retrying in 10s.", id, e.getMessage());
            scheduleReconnect();
        }
    }

    private void scheduleReconnect() {
        if (shuttingDown) {
            return;
        }
        try {
            scheduler.schedule(this::connect, 10, TimeUnit.SECONDS);
        } catch (Exception ignored) {
            // scheduler shutting down
        }
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        this.session = session;
        Map<String, String> connect = new LinkedHashMap<>();
        connect.put("t", "a");
        connect.put("uid", uid);
        connect.put("actid", actid);
        connect.put("accesstoken", accessToken);
        connect.put("source", "API");
        send(mapper.writeValueAsString(connect));
        log.debug("Feed connection {} sent connect frame", id);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonNode node = mapper.readTree(message.getPayload());
        String t = node.path("t").asText("");
        switch (t) {
            case "ak", "ck" -> {
                if ("OK".equalsIgnoreCase(node.path("s").asText(""))) {
                    log.info("Feed connection {} connected; subscribing to {} instruments.",
                            id, keys.size());
                    subscribe();
                    startHeartbeat();
                    reportState(true);
                } else {
                    log.error("Feed connection {} acknowledgement not OK: {}", id, message.getPayload());
                }
            }
            case "tk", "tf" -> {
                if (FIRST_TICK_LOGGED.compareAndSet(false, true)) {
                    log.info("Receiving live market data from Shoonya. First message: {}",
                            message.getPayload());
                }
                ingest.applyTouchline(node);
            }
            default -> {
                // only connect ack and touchline are relevant to this info-only app
            }
        }
    }

    private void subscribe() throws Exception {
        Map<String, String> sub = new LinkedHashMap<>();
        sub.put("t", "t"); // touchline
        sub.put("k", String.join("#", keys));
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
                log.debug("Feed connection {} heartbeat failed: {}", id, e.getMessage());
            }
        }, 3, 3, TimeUnit.SECONDS);
    }

    private synchronized void send(String payload) throws Exception {
        WebSocketSession s = this.session;
        if (s != null && s.isOpen()) {
            s.sendMessage(new TextMessage(payload));
        }
    }

    private void reportState(boolean connected) {
        if (connected == connectedReported) {
            return; // no change
        }
        connectedReported = connected;
        try {
            onStateChange.accept(id, connected);
        } catch (Exception ignored) {
            // never let a callback break the feed
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("Feed connection {} transport error: {}", id, exception.getMessage());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) {
        log.warn("Feed connection {} closed ({}). Reconnecting.", id, closeStatus);
        if (heartbeat != null) {
            heartbeat.cancel(false);
        }
        reportState(false);
        scheduleReconnect();
    }

    /** Stops the connection and releases its resources. */
    public void shutdown() {
        shuttingDown = true;
        if (heartbeat != null) {
            heartbeat.cancel(false);
        }
        scheduler.shutdownNow();
        try {
            WebSocketSession s = this.session;
            if (s != null && s.isOpen()) {
                s.close();
            }
        } catch (Exception ignored) {
            // best effort
        }
        reportState(false);
    }
}
