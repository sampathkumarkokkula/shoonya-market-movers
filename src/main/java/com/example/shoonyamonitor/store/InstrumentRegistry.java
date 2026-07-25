package com.example.shoonyamonitor.store;

import com.example.shoonyamonitor.config.ShoonyaProperties;
import com.example.shoonyamonitor.model.Instrument;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses the configured indices + watchlist once at startup and exposes
 * convenient lookups keyed by {@code EXCH|TOKEN}.
 */
@Component
public class InstrumentRegistry {

    private static final Logger log = LoggerFactory.getLogger(InstrumentRegistry.class);

    private final ShoonyaProperties props;

    private final Map<String, Instrument> byKey = new LinkedHashMap<>();
    private final List<Instrument> indices = new ArrayList<>();
    private final List<Instrument> watchlist = new ArrayList<>();

    public InstrumentRegistry(ShoonyaProperties props) {
        this.props = props;
    }

    @PostConstruct
    void init() {
        for (String raw : props.getIndices()) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            Instrument i = Instrument.parse(raw, true);
            indices.add(i);
            byKey.put(i.key(), i);
        }
        for (String raw : props.getWatchlist()) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            Instrument i = Instrument.parse(raw, false);
            watchlist.add(i);
            byKey.putIfAbsent(i.key(), i);
        }
        log.info("Instrument registry ready: {} indices, {} watchlist scrips",
                indices.size(), watchlist.size());
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
