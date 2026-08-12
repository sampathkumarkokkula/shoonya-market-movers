package com.example.shoonyamonitor.shoonya;

import com.example.shoonyamonitor.config.ShoonyaProperties;
import com.example.shoonyamonitor.model.Instrument;
import com.example.shoonyamonitor.store.InstrumentRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Orchestrates the live Shoonya (Noren) OAuth market-data feed.
 *
 * <p>Only created when {@code shoonya.mock=false}. It authenticates once via
 * {@link ShoonyaAuthService}, splits the full instrument universe into batches
 * that respect the per-connection {@code subscription-limit}, and opens one
 * {@link FeedConnection} per batch (up to {@code max-connections}). Each
 * connection subscribes its batch, heartbeats, and reconnects independently.
 * The overall feed is reported connected while at least one connection is up.</p>
 */
@Component
@ConditionalOnProperty(name = "shoonya.mock", havingValue = "false")
public class ShoonyaFeedClient {

    private static final Logger log = LoggerFactory.getLogger(ShoonyaFeedClient.class);

    private final ShoonyaProperties props;
    private final ShoonyaAuthService authService;
    private final InstrumentRegistry registry;
    private final FeedIngest ingest;
    private final FeedStatus status;
    private final ObjectMapper mapper = new ObjectMapper();
    private final StandardWebSocketClient client = new StandardWebSocketClient();

    private final List<FeedConnection> connections = new CopyOnWriteArrayList<>();
    private final AtomicInteger connectedCount = new AtomicInteger(0);
    private volatile boolean shuttingDown;

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
        openConnections();
    }

    /** Authenticates and opens one connection per subscription batch. */
    private synchronized void openConnections() {
        if (shuttingDown) {
            return;
        }
        String token;
        try {
            log.info("Authenticating with Shoonya...");
            token = authService.login();
        } catch (Exception e) {
            log.error("Authentication failed ({}). The feed cannot start until a valid "
                    + "access token or auth code is provided.", e.getMessage());
            status.setConnected(false);
            return;
        }

        String uid = props.getUserId();
        String actid = authService.resolvedAccountId();

        List<String> keys = registry.all().stream().map(Instrument::key).toList();
        List<List<String>> batches = batch(keys);
        log.info("Opening {} feed connection(s) for {} instruments (limit {}/connection).",
                batches.size(), keys.size(), props.getSubscriptionLimit());

        int id = 0;
        for (List<String> b : batches) {
            FeedConnection conn = new FeedConnection(id++, props.getWsUrl(), uid, actid, token,
                    b, ingest, mapper, client, this::onConnectionState);
            connections.add(conn);
            conn.connect();
        }
    }

    /**
     * Splits the keys into batches no larger than the subscription limit,
     * capped at the configured maximum number of connections. Logs a shortfall
     * if the universe exceeds total capacity.
     */
    private List<List<String>> batch(List<String> keys) {
        int limit = Math.max(1, props.getSubscriptionLimit());
        int maxConns = Math.max(1, props.getMaxConnections());
        int capacity = limit * maxConns;

        List<String> covered = keys;
        if (keys.size() > capacity) {
            log.warn("Universe of {} instruments exceeds capacity {} ({} connections x {} "
                            + "tokens). {} instruments will NOT be subscribed.",
                    keys.size(), capacity, maxConns, limit, keys.size() - capacity);
            covered = keys.subList(0, capacity);
        }

        List<List<String>> batches = new ArrayList<>();
        for (int i = 0; i < covered.size(); i += limit) {
            batches.add(new ArrayList<>(covered.subList(i, Math.min(i + limit, covered.size()))));
        }
        return batches;
    }

    /** Callback from each connection when it connects or disconnects. */
    private void onConnectionState(int connectionId, boolean connected) {
        int now = connected ? connectedCount.incrementAndGet() : connectedCount.decrementAndGet();
        int active = Math.max(0, now);
        status.setConnected(active > 0);
        log.info("Feed connection {} {} - {}/{} connections active.",
                connectionId, connected ? "up" : "down", active, connections.size());
    }

    /**
     * Rebuilds all connections from the current registry (used after a daily
     * universe refresh). Closes existing connections and reopens fresh ones.
     */
    public synchronized void restart() {
        if (shuttingDown) {
            return;
        }
        log.info("Restarting feed connections to apply the refreshed universe.");
        closeAll();
        openConnections();
    }

    private void closeAll() {
        for (FeedConnection c : connections) {
            c.shutdown();
        }
        connections.clear();
        connectedCount.set(0);
        status.setConnected(false);
    }

    @PreDestroy
    public void shutdown() {
        shuttingDown = true;
        closeAll();
    }
}
