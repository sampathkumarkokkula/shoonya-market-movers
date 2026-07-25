package com.example.shoonyamonitor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Strongly typed view of every {@code shoonya.*} property.
 */
@ConfigurationProperties(prefix = "shoonya")
public class ShoonyaProperties {

    /** When true the app produces synthetic ticks and needs no credentials. */
    private boolean mock = true;

    private String restBase = "https://api.shoonya.com/NorenWClientTP/";
    private String wsUrl = "wss://api.shoonya.com/NorenWSTP/";

    private String userId = "";
    private String password = "";
    private String vendorCode = "";
    private String apiKey = "";
    private String imei = "abc1234";
    private String factor2 = "";
    private String totpSecret = "";

    /** Raw "EXCH|TOKEN|NAME" entries for the indices shown at the top. */
    private List<String> indices = new ArrayList<>();
    /** Raw "EXCH|TOKEN|NAME" entries scanned for gainers/losers. */
    private List<String> watchlist = new ArrayList<>();

    private int topN = 10;
    private long broadcastIntervalMs = 5000;
    private long retentionMs = 720_000;
    /** Movement windows, in seconds. */
    private List<Integer> windowsSeconds = new ArrayList<>(List.of(15, 30, 60, 120, 300, 600));

    // --- getters / setters ------------------------------------------------

    public boolean isMock() {
        return mock;
    }

    public void setMock(boolean mock) {
        this.mock = mock;
    }

    public String getRestBase() {
        return restBase;
    }

    public void setRestBase(String restBase) {
        this.restBase = restBase;
    }

    public String getWsUrl() {
        return wsUrl;
    }

    public void setWsUrl(String wsUrl) {
        this.wsUrl = wsUrl;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getVendorCode() {
        return vendorCode;
    }

    public void setVendorCode(String vendorCode) {
        this.vendorCode = vendorCode;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getImei() {
        return imei;
    }

    public void setImei(String imei) {
        this.imei = imei;
    }

    public String getFactor2() {
        return factor2;
    }

    public void setFactor2(String factor2) {
        this.factor2 = factor2;
    }

    public String getTotpSecret() {
        return totpSecret;
    }

    public void setTotpSecret(String totpSecret) {
        this.totpSecret = totpSecret;
    }

    public List<String> getIndices() {
        return indices;
    }

    public void setIndices(List<String> indices) {
        this.indices = indices;
    }

    public List<String> getWatchlist() {
        return watchlist;
    }

    public void setWatchlist(List<String> watchlist) {
        this.watchlist = watchlist;
    }

    public int getTopN() {
        return topN;
    }

    public void setTopN(int topN) {
        this.topN = topN;
    }

    public long getBroadcastIntervalMs() {
        return broadcastIntervalMs;
    }

    public void setBroadcastIntervalMs(long broadcastIntervalMs) {
        this.broadcastIntervalMs = broadcastIntervalMs;
    }

    public long getRetentionMs() {
        return retentionMs;
    }

    public void setRetentionMs(long retentionMs) {
        this.retentionMs = retentionMs;
    }

    public List<Integer> getWindowsSeconds() {
        return windowsSeconds;
    }

    public void setWindowsSeconds(List<Integer> windowsSeconds) {
        this.windowsSeconds = windowsSeconds;
    }
}
