package com.example.shoonyamonitor.store;

import com.example.shoonyamonitor.config.ShoonyaProperties;
import com.example.shoonyamonitor.model.Instrument;
import com.example.shoonyamonitor.model.ScripState;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory, time-windowed price history plus the latest {@link ScripState}
 * for every subscribed instrument.
 *
 * <p>Each instrument keeps an append-only {@link Deque} of {@link Point}
 * samples ordered by time. Old samples beyond the retention window are pruned
 * so memory stays bounded. Reads (baseline lookups) happen every few seconds
 * from the broadcaster thread while writes arrive continuously from the feed
 * thread, so the per-key deque is guarded by its own monitor.</p>
 */
@Component
public class TickStore {

    /** A single timestamped price sample. */
    public record Point(long ts, double price) {
    }

    private final ShoonyaProperties props;
    private final InstrumentRegistry registry;

    private final Map<String, ScripState> states = new ConcurrentHashMap<>();
    private final Map<String, Deque<Point>> history = new ConcurrentHashMap<>();
    private final AtomicLong totalTicks = new AtomicLong();
    private volatile long startedAt = System.currentTimeMillis();

    public TickStore(ShoonyaProperties props, InstrumentRegistry registry) {
        this.props = props;
        this.registry = registry;
    }

    @PostConstruct
    void seedStates() {
        for (Instrument i : registry.all()) {
            states.put(i.key(), new ScripState(i.key(), i.exchange(), i.token(), i.displayName()));
            history.put(i.key(), new ArrayDeque<>());
        }
        startedAt = System.currentTimeMillis();
    }

    public ScripState state(String key) {
        return states.computeIfAbsent(key, k -> {
            Instrument i = registry.byKey(k);
            String exch = i != null ? i.exchange() : "";
            String token = i != null ? i.token() : k;
            String name = i != null ? i.displayName() : k;
            return new ScripState(k, exch, token, name);
        });
    }

    /**
     * Records a traded price for the given instrument and updates the state's
     * last price. Prunes samples older than the retention window.
     */
    public void recordTick(String key, double price, long ts) {
        if (price <= 0) {
            return;
        }
        state(key).applyPrice(price, ts);
        Deque<Point> dq = history.computeIfAbsent(key, k -> new ArrayDeque<>());
        long cutoff = ts - props.getRetentionMs();
        synchronized (dq) {
            dq.addLast(new Point(ts, price));
            while (!dq.isEmpty() && dq.peekFirst().ts() < cutoff) {
                dq.pollFirst();
            }
        }
        totalTicks.incrementAndGet();
    }

    /**
     * Baseline price as of {@code now - windowMs}: the most recent sample at or
     * before that target time. Returns {@code null} when there is not yet
     * enough history to cover the whole window.
     */
    public Double baselinePrice(String key, long windowMs, long now) {
        Deque<Point> dq = history.get(key);
        if (dq == null) {
            return null;
        }
        long target = now - windowMs;
        Double baseline = null;
        synchronized (dq) {
            if (dq.isEmpty() || dq.peekFirst().ts() > target) {
                // Oldest sample is newer than the target -> window not covered yet.
                return null;
            }
            for (Point p : dq) {
                if (p.ts() <= target) {
                    baseline = p.price();
                } else {
                    break;
                }
            }
        }
        return baseline;
    }

    public int trackedScrips() {
        return states.size();
    }

    public long totalTicks() {
        return totalTicks.get();
    }

    public long uptimeMs() {
        return System.currentTimeMillis() - startedAt;
    }
}
