package com.example.shoonyamonitor.store;

import com.example.shoonyamonitor.config.ShoonyaProperties;
import com.example.shoonyamonitor.model.Instrument;
import com.example.shoonyamonitor.shoonya.SymbolMasterService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Holds the set of monitored instruments keyed by {@code EXCHANGE|TOKEN}.
 *
 * <p>Indices always come from configuration. The watchlist is either the full
 * NSE EQ universe (loaded from the symbol master when
 * {@code shoonya.universe-enabled=true}) or the hardcoded configuration list.
 * The registry can be {@link #reload() reloaded} at runtime (e.g. by the daily
 * refresh); reads see a consistent snapshot via volatile references.</p>
 */
@Component
public class InstrumentRegistry {

    private static final Logger log = LoggerFactory.getLogger(InstrumentRegistry.class);

    private final ShoonyaProperties props;
    private final SymbolMasterService symbolMaster;

    private volatile Map<String, Instrument> byKey = new LinkedHashMap<>();
    private volatile List<Instrument> indices = new ArrayList<>();
    private volatile List<Instrument> watchlist = new ArrayList<>();

    public InstrumentRegistry(ShoonyaProperties props, SymbolMasterService symbolMaster) {
        this.props = props;
        this.symbolMaster = symbolMaster;
    }

    @PostConstruct
    void init() {
        populate();
    }

    /**
     * Rebuilds the instrument set from configuration and (when enabled) the
     * symbol master, atomically swapping in the new snapshot.
     *
     * @return the total number of instruments after population
     */
    public synchronized int reload() {
        populate();
        return byKey.size();
    }

    private void populate() {
        Map<String, Instrument> newByKey = new LinkedHashMap<>();
        List<Instrument> newIndices = new ArrayList<>();
        List<Instrument> newWatchlist = new ArrayList<>();

        for (String raw : props.getIndices()) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            Instrument i = Instrument.parse(raw, true);
            newIndices.add(i);
            newByKey.put(i.key(), i);
        }

        List<Instrument> universe = resolveWatchlist();
        for (Instrument i : universe) {
            if (newByKey.containsKey(i.key())) {
                continue; // already present (e.g. also an index) - keep one
            }
            newWatchlist.add(i);
            newByKey.put(i.key(), i);
        }

        this.indices = newIndices;
        this.watchlist = newWatchlist;
        this.byKey = newByKey;

        log.info("Instrument registry ready: {} indices, {} watchlist scrips",
                newIndices.size(), newWatchlist.size());
    }

    /** Resolves the watchlist from the symbol master universe or config. */
    private List<Instrument> resolveWatchlist() {
        if (props.isUniverseEnabled()) {
            List<Instrument> universe = symbolMaster.loadUniverse();
            if (!universe.isEmpty()) {
                return universe;
            }
            log.warn("Universe enabled but symbol master returned no instruments; "
                    + "falling back to the configured watchlist.");
        }
        List<Instrument> configured = new ArrayList<>();
        for (String raw : props.getWatchlist()) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            configured.add(Instrument.parse(raw, false));
        }
        return configured;
    }

    public List<Instrument> indices() {
        return indices;
    }

    public List<Instrument> watchlist() {
        return watchlist;
    }

    /** Every subscribable instrument (indices + watchlist), indices first. */
    public List<Instrument> all() {
        return new ArrayList<>(byKey.values());
    }

    public Instrument byKey(String key) {
        return byKey.get(key);
    }
}
