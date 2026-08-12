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

    // NOTE: Shoonya migrated to the OAuth API. The old NorenWClientTP /
    // NorenWSTP endpoints are retired (they now return HTTP 502). The live
    // endpoints are NorenWClientAPI (REST) and NorenWSAPI (WebSocket).
    private String restBase = "https://api.shoonya.com/NorenWClientAPI/";
    private String wsUrl = "wss://api.shoonya.com/NorenWSAPI/";

    private String userId = "";

    /**
     * OAuth session token. This is the {@code access_token} produced by the
     * Shoonya OAuth flow (browser login -> auth code -> GenAcsTok exchange).
     * When set, it is used directly as the WebSocket session token, replacing
     * the retired QuickAuth login.
     */
    private String accessToken = "";

    /**
     * Trading account id returned alongside the access token. Defaults to the
     * user id when left blank (they are the same for most retail accounts).
     */
    private String accountId = "";

    // --- OAuth auth-code exchange (used when access-token is not supplied) ---
    /** OAuth app client id (the API key of your OAuth app). */
    private String clientId = "";
    /** OAuth app secret code. Required to compute the GenAcsTok checksum. */
    private String secretCode = "";
    /**
     * The one-time auth code from the OAuth redirect URL (the {@code code=}
     * value). When set (and no access-token is given) the app exchanges it for
     * an access token at GenAcsTok. May be pasted as the raw code or the whole
     * redirect URL - it is sanitised before use.
     */
    private String authCode = "";

    // --- legacy QuickAuth fields (only used by the deprecated login path) ---
    private String password = "";
    private String vendorCode = "";
    private String apiKey = "";
    private String imei = "abc1234";
    private String factor2 = "";
    private String totpSecret = "";

    /** Raw "EXCH|TOKEN|NAME" entries for the indices shown at the top. */
    private List<String> indices = new ArrayList<>();
    /** Raw "EXCH|TOKEN|NAME" entries scanned for gainers/losers (fallback,
     *  used only when the symbol-master universe is disabled). */
    private List<String> watchlist = new ArrayList<>();

    // --- Full-universe (symbol master) settings -------------------------------
    /** When true, the watchlist is built from the exchange symbol master
     *  (all NSE EQ stocks) instead of the hardcoded {@link #watchlist}. */
    private boolean universeEnabled = false;
    /** URL of the NSE symbol master zip published by Shoonya. */
    private String symbolMasterUrl = "https://api.shoonya.com/NSE_symbols.txt.zip";
    /** Directory used to cache the downloaded symbol master for offline
     *  fallback. Blank means the system temp directory. */
    private String symbolMasterCache = "";
    /** Max tokens subscribed on one feed connection (Noren limit ~1000). */
    private int subscriptionLimit = 1000;
    /** Max concurrent feed connections used to cover the universe. */
    private int maxConnections = 5;
    /** Liquidity filter: minimum traded volume for an instrument to be ranked
     *  (0 disables the volume check). */
    private long minVolume = 50_000;
    /** Liquidity filter: minimum last price for an instrument to be ranked
     *  (0 disables the price check). */
    private double minPrice = 10.0;

    private int topN = 20;
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

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    /** Account id, falling back to the user id when not explicitly set. */
    public String getAccountId() {
        return (accountId == null || accountId.isBlank()) ? userId : accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getSecretCode() {
        return secretCode;
    }

    public void setSecretCode(String secretCode) {
        this.secretCode = secretCode;
    }

    public String getAuthCode() {
        return authCode;
    }

    public void setAuthCode(String authCode) {
        this.authCode = authCode;
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

    public boolean isUniverseEnabled() {
        return universeEnabled;
    }

    public void setUniverseEnabled(boolean universeEnabled) {
        this.universeEnabled = universeEnabled;
    }

    public String getSymbolMasterUrl() {
        return symbolMasterUrl;
    }

    public void setSymbolMasterUrl(String symbolMasterUrl) {
        this.symbolMasterUrl = symbolMasterUrl;
    }

    public String getSymbolMasterCache() {
        return symbolMasterCache;
    }

    public void setSymbolMasterCache(String symbolMasterCache) {
        this.symbolMasterCache = symbolMasterCache;
    }

    public int getSubscriptionLimit() {
        return subscriptionLimit;
    }

    public void setSubscriptionLimit(int subscriptionLimit) {
        this.subscriptionLimit = subscriptionLimit;
    }

    public int getMaxConnections() {
        return maxConnections;
    }

    public void setMaxConnections(int maxConnections) {
        this.maxConnections = maxConnections;
    }

    public long getMinVolume() {
        return minVolume;
    }

    public void setMinVolume(long minVolume) {
        this.minVolume = minVolume;
    }

    public double getMinPrice() {
        return minPrice;
    }

    public void setMinPrice(double minPrice) {
        this.minPrice = minPrice;
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
