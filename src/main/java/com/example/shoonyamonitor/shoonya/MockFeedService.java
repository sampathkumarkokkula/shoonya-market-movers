package com.example.shoonyamonitor.shoonya;

import com.example.shoonyamonitor.model.Instrument;
import com.example.shoonyamonitor.model.ScripState;
import com.example.shoonyamonitor.store.InstrumentRegistry;
import com.example.shoonyamonitor.store.TickStore;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Synthetic market data generator used when {@code shoonya.mock=true}
 * (the default). It seeds every instrument with a plausible opening price and
 * then random-walks each price a few times per second, giving the dashboard a
 * realistic, always-on feed with no credentials required.
 */
@Component
@ConditionalOnProperty(name = "shoonya.mock", havingValue = "true", matchIfMissing = true)
public class MockFeedService {

    private static final Logger log = LoggerFactory.getLogger(MockFeedService.class);

    private final InstrumentRegistry registry;
    private final TickStore store;
    private final FeedStatus status;

    /** Per-instrument drift bias so some names trend up and others down. */
    private final Map<String, Double> drift = new HashMap<>();

    public MockFeedService(InstrumentRegistry registry, TickStore store, FeedStatus status) {
        this.registry = registry;
        this.store = store;
        this.status = status;
    }

    @PostConstruct
    void seed() {
        status.setMode("MOCK");
        status.setConnected(true);
        long now = System.currentTimeMillis();
        for (Instrument i : registry.all()) {
            double base = basePrice(i);
            double prevClose = round(base * (1 + gaussian(0.004)));
            ScripState s = store.state(i.key());
            s.applyPrevClose(prevClose);
            s.applyOpen(round(prevClose * (1 + gaussian(0.003))));
            s.applyHigh(s.getOpen());
            s.applyLow(s.getOpen());
            s.applyVolume(ThreadLocalRandom.current().nextLong(50_000, 5_000_000));
            store.recordTick(i.key(), s.getOpen(), now);
            // Bias: half trend up, half down, magnitude varies.
            drift.put(i.key(), gaussian(0.00015));
        }
        log.info("Mock feed seeded {} instruments", registry.all().size());
    }

    @Scheduled(fixedRate = 300)
    void tick() {
        long now = System.currentTimeMillis();
        for (Instrument i : registry.all()) {
            ScripState s = store.state(i.key());
            double last = s.getLastPrice();
            if (last <= 0) {
                continue;
            }
            double d = drift.getOrDefault(i.key(), 0.0);
            // Random walk: small noise + gentle per-instrument drift.
            double changePct = gaussian(0.0012) + d;
            double next = round(Math.max(0.05, last * (1 + changePct)));
            s.applyHigh(Math.max(s.getHigh(), next));
            s.applyLow(s.getLow() <= 0 ? next : Math.min(s.getLow(), next));
            s.applyVolume(s.getVolume() + ThreadLocalRandom.current().nextLong(0, 25_000));
            double range = s.getHigh() - s.getLow();
            s.applyAvg(range > 0 ? round(s.getLow() + range / 2.0) : next);
            store.recordTick(i.key(), next, now);
        }
    }

    private static double basePrice(Instrument i) {
        String name = i.displayName().toUpperCase();
        if (name.contains("SENSEX")) {
            return 80_500;
        }
        if (name.contains("NIFTY")) {
            return 24_500;
        }
        // Deterministic-ish base per symbol so restarts look similar.
        int hash = Math.abs(i.key().hashCode());
        return 200 + (hash % 3800);
    }

    /** Gaussian noise scaled by {@code scale}. */
    private static double gaussian(double scale) {
        return ThreadLocalRandom.current().nextGaussian() * scale;
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
