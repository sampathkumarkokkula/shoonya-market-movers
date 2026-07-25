package com.example.shoonyamonitor.model;

/**
 * Latest known snapshot of a single scrip, built by merging the initial
 * touchline acknowledgement ({@code tk}) with subsequent partial feeds
 * ({@code tf}). All mutation and reads are synchronized because ticks arrive
 * on the feed thread while the broadcaster reads on a scheduler thread.
 */
public class ScripState {

    private final String key;
    private final String exchange;
    private final String token;

    private volatile String symbol;
    private double lastPrice;
    private double prevClose;
    private double open;
    private double high;
    private double low;
    private double avgPrice;
    private long volume;
    private long lastUpdate;
    private boolean hasPrice;

    public ScripState(String key, String exchange, String token, String symbol) {
        this.key = key;
        this.exchange = exchange;
        this.token = token;
        this.symbol = symbol;
    }

    public synchronized void updateSymbol(String s) {
        if (s != null && !s.isBlank()) {
            this.symbol = s;
        }
    }

    public synchronized void applyPrice(double lp, long ts) {
        this.lastPrice = lp;
        this.hasPrice = true;
        this.lastUpdate = ts;
    }

    public synchronized void applyPrevClose(double c) {
        this.prevClose = c;
    }

    public synchronized void applyOpen(double o) {
        this.open = o;
    }

    public synchronized void applyHigh(double h) {
        this.high = h;
    }

    public synchronized void applyLow(double l) {
        this.low = l;
    }

    public synchronized void applyAvg(double ap) {
        this.avgPrice = ap;
    }

    public synchronized void applyVolume(long v) {
        this.volume = v;
    }

    public synchronized void touch(long ts) {
        this.lastUpdate = ts;
    }

    // --- reads ------------------------------------------------------------

    public String getKey() {
        return key;
    }

    public String getExchange() {
        return exchange;
    }

    public String getToken() {
        return token;
    }

    public String getSymbol() {
        return symbol;
    }

    public synchronized double getLastPrice() {
        return lastPrice;
    }

    public synchronized double getPrevClose() {
        return prevClose;
    }

    public synchronized double getOpen() {
        return open;
    }

    public synchronized double getHigh() {
        return high;
    }

    public synchronized double getLow() {
        return low;
    }

    public synchronized double getAvgPrice() {
        return avgPrice;
    }

    public synchronized long getVolume() {
        return volume;
    }

    public synchronized long getLastUpdate() {
        return lastUpdate;
    }

    public synchronized boolean hasPrice() {
        return hasPrice;
    }

    /** Percentage change versus previous close, or 0 if unknown. */
    public synchronized double dayChangePct() {
        if (prevClose <= 0) {
            return 0;
        }
        return (lastPrice - prevClose) / prevClose * 100.0;
    }

    /** Position of last price inside the day's range [0..100], or -1 if unknown. */
    public synchronized double rangePositionPct() {
        double span = high - low;
        if (span <= 0) {
            return -1;
        }
        double pos = (lastPrice - low) / span * 100.0;
        return Math.max(0, Math.min(100, pos));
    }
}
