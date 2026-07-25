package com.example.shoonyamonitor.service;

import com.example.shoonyamonitor.config.ShoonyaProperties;
import com.example.shoonyamonitor.dto.IndexQuote;
import com.example.shoonyamonitor.dto.IntervalRanking;
import com.example.shoonyamonitor.dto.MovementRow;
import com.example.shoonyamonitor.dto.Snapshot;
import com.example.shoonyamonitor.model.Instrument;
import com.example.shoonyamonitor.model.ScripState;
import com.example.shoonyamonitor.shoonya.FeedStatus;
import com.example.shoonyamonitor.store.InstrumentRegistry;
import com.example.shoonyamonitor.store.TickStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Turns the raw {@link TickStore} state into the ranked {@link Snapshot} that
 * the dashboard consumes: index quotes, plus top gainers and losers over each
 * configured time window, enriched with a short insight per row.
 */
@Service
public class MovementService {

    private final ShoonyaProperties props;
    private final InstrumentRegistry registry;
    private final TickStore store;
    private final FeedStatus status;

    public MovementService(ShoonyaProperties props,
                           InstrumentRegistry registry,
                           TickStore store,
                           FeedStatus status) {
        this.props = props;
        this.registry = registry;
        this.store = store;
        this.status = status;
    }

    public Snapshot buildSnapshot() {
        long now = System.currentTimeMillis();

        List<IndexQuote> indices = new ArrayList<>();
        for (Instrument i : registry.indices()) {
            indices.add(toIndexQuote(store.state(i.key())));
        }

        List<IntervalRanking> intervals = new ArrayList<>();
        for (int seconds : props.getWindowsSeconds()) {
            intervals.add(buildInterval(seconds, now));
        }

        Snapshot.Stats stats = new Snapshot.Stats(
                store.trackedScrips(),
                store.totalTicks(),
                store.uptimeMs() / 1000);

        return new Snapshot(now, status.getMode(), status.isConnected(), indices, intervals, stats);
    }

    private IntervalRanking buildInterval(int seconds, long now) {
        long windowMs = seconds * 1000L;
        boolean ready = store.uptimeMs() >= windowMs;

        List<MovementRow> rows = new ArrayList<>();
        for (Instrument i : registry.watchlist()) {
            ScripState s = store.state(i.key());
            double ltp = s.getLastPrice();
            if (ltp <= 0) {
                continue;
            }
            Double baseline = store.baselinePrice(i.key(), windowMs, now);
            if (baseline == null || baseline <= 0) {
                continue;
            }
            double changeAbs = ltp - baseline;
            double changePct = changeAbs / baseline * 100.0;
            rows.add(new MovementRow(
                    s.getSymbol(),
                    s.getExchange(),
                    s.getToken(),
                    round(ltp),
                    round(changeAbs),
                    round(changePct),
                    round(s.dayChangePct()),
                    s.getVolume(),
                    round(s.rangePositionPct()),
                    insightFor(i.key(), s, now)));
        }

        List<MovementRow> gainers = rows.stream()
                .filter(r -> r.changePct() > 0)
                .sorted(Comparator.comparingDouble(MovementRow::changePct).reversed())
                .limit(props.getTopN())
                .toList();

        List<MovementRow> losers = rows.stream()
                .filter(r -> r.changePct() < 0)
                .sorted(Comparator.comparingDouble(MovementRow::changePct))
                .limit(props.getTopN())
                .toList();

        return new IntervalRanking(label(seconds), seconds, ready, gainers, losers);
    }

    private IndexQuote toIndexQuote(ScripState s) {
        double ltp = s.getLastPrice();
        double prev = s.getPrevClose();
        double change = (prev > 0) ? ltp - prev : 0;
        double pct = (prev > 0) ? change / prev * 100.0 : 0;
        return new IndexQuote(
                s.getSymbol(),
                round(ltp),
                round(change),
                round(pct),
                round(s.getOpen()),
                round(s.getHigh()),
                round(s.getLow()),
                round(prev),
                s.hasPrice());
    }

    /**
     * Builds a short context string by combining the short-term (30s) and
     * medium-term (5m) direction with the position inside the day's range.
     */
    private String insightFor(String key, ScripState s, long now) {
        List<String> parts = new ArrayList<>();

        Double b30 = store.baselinePrice(key, 30_000, now);
        Double b300 = store.baselinePrice(key, 300_000, now);
        double ltp = s.getLastPrice();
        Double shortPct = (b30 != null && b30 > 0) ? (ltp - b30) / b30 * 100 : null;
        Double longPct = (b300 != null && b300 > 0) ? (ltp - b300) / b300 * 100 : null;

        if (shortPct != null && longPct != null) {
            if (shortPct > 0 && longPct > 0) {
                parts.add("gaining momentum");
            } else if (shortPct < 0 && longPct < 0) {
                parts.add("under pressure");
            } else if (shortPct > 0 && longPct <= 0) {
                parts.add("rebounding");
            } else if (shortPct < 0 && longPct >= 0) {
                parts.add("pulling back");
            }
        }

        double range = s.rangePositionPct();
        if (range >= 0) {
            if (range >= 90) {
                parts.add("near day high");
            } else if (range <= 10) {
                parts.add("near day low");
            }
        }

        double dayPct = s.dayChangePct();
        if (dayPct != 0) {
            parts.add(String.format("%+.2f%% today", dayPct));
        }

        return String.join(" \u00b7 ", parts);
    }

    private static String label(int seconds) {
        if (seconds < 60) {
            return seconds + "s";
        }
        return (seconds / 60) + "m";
    }

    private static double round(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) {
            return 0;
        }
        return Math.round(v * 100.0) / 100.0;
    }
}
