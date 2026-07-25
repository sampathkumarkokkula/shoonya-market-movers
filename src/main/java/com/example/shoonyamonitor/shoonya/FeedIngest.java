package com.example.shoonyamonitor.shoonya;

import com.example.shoonyamonitor.model.ScripState;
import com.example.shoonyamonitor.store.TickStore;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

/**
 * Applies a Noren touchline message ({@code tk} snapshot or {@code tf} delta)
 * to the {@link TickStore}. Feed fields arrive as strings and any field may be
 * absent in a {@code tf} delta, so every field is parsed defensively and only
 * applied when present.
 */
@Component
public class FeedIngest {

    private final TickStore store;

    public FeedIngest(TickStore store) {
        this.store = store;
    }

    /**
     * @param node a parsed {@code tk}/{@code tf} JSON object
     */
    public void applyTouchline(JsonNode node) {
        String exch = text(node, "e");
        String token = text(node, "tk");
        if (exch == null || token == null) {
            return;
        }
        String key = exch + "|" + token;
        ScripState state = store.state(key);
        long now = System.currentTimeMillis();

        String symbol = text(node, "ts");
        if (symbol != null) {
            state.updateSymbol(symbol);
        }
        Double open = dbl(node, "o");
        if (open != null) {
            state.applyOpen(open);
        }
        Double high = dbl(node, "h");
        if (high != null) {
            state.applyHigh(high);
        }
        Double low = dbl(node, "l");
        if (low != null) {
            state.applyLow(low);
        }
        Double close = dbl(node, "c");
        if (close != null) {
            state.applyPrevClose(close);
        }
        Double avg = dbl(node, "ap");
        if (avg != null) {
            state.applyAvg(avg);
        }
        Long vol = lng(node, "v");
        if (vol != null) {
            state.applyVolume(vol);
        }

        Double lp = dbl(node, "lp");
        if (lp != null) {
            // recordTick updates last price AND appends to the history buffer.
            store.recordTick(key, lp, now);
        } else {
            state.touch(now);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) {
            return null;
        }
        String s = v.asText();
        return s.isBlank() ? null : s;
    }

    private static Double dbl(JsonNode node, String field) {
        String s = text(node, field);
        if (s == null) {
            return null;
        }
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Long lng(JsonNode node, String field) {
        String s = text(node, field);
        if (s == null) {
            return null;
        }
        try {
            return (long) Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
