package com.example.shoonyamonitor.shoonya;

import com.example.shoonyamonitor.config.ShoonyaProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Resolves the session token needed to open the market-data WebSocket.
 *
 * <p>Shoonya migrated to an OAuth flow. The supported path is to supply an
 * OAuth {@code access_token} (obtained once per session via Shoonya's browser
 * login + {@code GenAcsTok} exchange) through {@code shoonya.access-token};
 * that token is used directly as the WebSocket session token.</p>
 *
 * <p>The former {@code QuickAuth} login is kept only as a deprecated fallback.
 * Its endpoint ({@code NorenWClientTP}) is retired and now returns HTTP 502, so
 * it will not establish a live feed.</p>
 */
@Service
public class ShoonyaAuthService {

    private static final Logger log = LoggerFactory.getLogger(ShoonyaAuthService.class);

    private final ShoonyaProperties props;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    /** Account id resolved during login; used for the WebSocket connect frame. */
    private volatile String sessionActid;

    public ShoonyaAuthService(ShoonyaProperties props) {
        this.props = props;
    }

    /**
     * Resolves the session token used to open the feed.
     *
     * <p>Order of preference: a ready access token, then an auth-code exchange,
     * then the deprecated QuickAuth login.</p>
     *
     * @throws IllegalStateException if no token is available or login fails
     */
    public String login() {
        require(props.getUserId(), "shoonya.user-id");
        sessionActid = props.getAccountId();

        String accessToken = props.getAccessToken();
        if (accessToken != null && !accessToken.isBlank()) {
            log.info("Using pre-obtained OAuth access token for uid {} (account {})",
                    props.getUserId(), sessionActid);
            return accessToken.trim();
        }

        if (props.getAuthCode() != null && !props.getAuthCode().isBlank()) {
            return exchangeAuthCode();
        }

        log.warn("No shoonya.access-token or shoonya.auth-code set - falling back to "
                + "the DEPRECATED QuickAuth login. Its endpoint is retired and will "
                + "likely fail with HTTP 502.");
        return quickAuthLogin();
    }

    /**
     * Account id resolved during the last {@link #login()} (from the token
     * exchange when available, otherwise the configured account/user id).
     */
    public String resolvedAccountId() {
        return (sessionActid == null || sessionActid.isBlank())
                ? props.getAccountId() : sessionActid;
    }

    /**
     * Exchanges the one-time OAuth auth code for an access token via the
     * {@code GenAcsTok} endpoint. The request checksum is
     * {@code sha256(clientId + secretCode + authCode)}.
     *
     * @return the OAuth access token used to open the feed
     * @throws IllegalStateException if required inputs are missing or the
     *                               exchange is rejected
     */
    private String exchangeAuthCode() {
        require(props.getClientId(), "shoonya.client-id");
        require(props.getSecretCode(), "shoonya.secret-code");

        String authCode = cleanAuthCode(props.getAuthCode());
        if (authCode.isBlank()) {
            throw new IllegalStateException("shoonya.auth-code is empty after sanitising");
        }

        String checksum = CryptoUtil.sha256Hex(props.getClientId() + props.getSecretCode() + authCode);

        Map<String, String> values = new LinkedHashMap<>();
        values.put("code", authCode);
        values.put("checksum", checksum);
        values.put("uid", props.getUserId());

        String jData;
        try {
            jData = mapper.writeValueAsString(values);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize token-exchange payload", e);
        }

        String body = "jData=" + jData;
        String url = normalize(props.getRestBase()) + "GenAcsTok";

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode root = mapper.readTree(response.body());

            String token = root.path("access_token").asText("");
            if (token.isBlank()) {
                String emsg = root.path("emsg").asText(response.body());
                throw new IllegalStateException(
                        "Auth code exchange failed (no access_token returned): " + emsg
                                + ". The auth code is single-use and short-lived - generate a "
                                + "fresh one, and verify shoonya.client-id / shoonya.secret-code.");
            }

            String actid = root.path("actid").asText("");
            if (!actid.isBlank()) {
                sessionActid = actid;
            }
            log.info("Access token obtained via GenAcsTok for uid {} (account {})",
                    props.getUserId(), resolvedAccountId());
            return token;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Token-exchange request failed: " + e.getMessage(), e);
        }
    }

    /**
     * Extracts the bare auth code from whatever the user pasted: the raw code,
     * a {@code code=...} fragment, or the whole redirect URL. Trailing URL
     * pieces such as {@code &state=...}, a {@code #} fragment, or trailing
     * slashes are removed.
     */
    static String cleanAuthCode(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim();
        int ci = s.indexOf("code=");
        if (ci >= 0) {
            s = s.substring(ci + "code=".length());
        }
        // Cut at the first URL delimiter that ends the code value.
        int cut = s.length();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '&' || c == '#' || c == '?' || c == ' ') {
                cut = i;
                break;
            }
        }
        s = s.substring(0, cut);
        while (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    /**
     * Legacy QuickAuth login. Retained for reference only; the underlying
     * endpoint is retired.
     *
     * @throws IllegalStateException if credentials are missing or login fails
     */
    private String quickAuthLogin() {
        require(props.getPassword(), "shoonya.password");
        require(props.getVendorCode(), "shoonya.vendor-code");
        require(props.getApiKey(), "shoonya.api-key");

        String factor2 = resolveFactor2();

        String pwdHash = CryptoUtil.sha256Hex(props.getPassword());
        String appKey = CryptoUtil.sha256Hex(props.getUserId() + "|" + props.getApiKey());

        Map<String, String> values = new LinkedHashMap<>();
        values.put("source", "API");
        values.put("apkversion", "1.0.0");
        values.put("uid", props.getUserId());
        values.put("pwd", pwdHash);
        values.put("factor2", factor2);
        values.put("vc", props.getVendorCode());
        values.put("appkey", appKey);
        values.put("imei", props.getImei());

        String jData;
        try {
            jData = mapper.writeValueAsString(values);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize login payload", e);
        }

        String body = "jData=" + URLEncoder.encode(jData, StandardCharsets.UTF_8);
        String url = normalize(props.getRestBase()) + "QuickAuth";

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode root = mapper.readTree(response.body());
            String stat = root.path("stat").asText("");
            if (!"Ok".equalsIgnoreCase(stat)) {
                String emsg = root.path("emsg").asText(response.body());
                throw new IllegalStateException("Shoonya login rejected: " + emsg);
            }
            String token = root.path("susertoken").asText("");
            if (token.isBlank()) {
                throw new IllegalStateException("Login succeeded but no susertoken returned");
            }
            log.info("Shoonya login successful for uid {}", props.getUserId());
            return token;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Shoonya login request failed: " + e.getMessage(), e);
        }
    }

    private String resolveFactor2() {
        if (props.getFactor2() != null && !props.getFactor2().isBlank()) {
            return props.getFactor2().trim();
        }
        if (props.getTotpSecret() != null && !props.getTotpSecret().isBlank()) {
            return TotpGenerator.now(props.getTotpSecret());
        }
        throw new IllegalStateException(
                "No second factor available: set shoonya.factor2 (6-digit OTP) or shoonya.totp-secret");
    }

    private static void require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required property: " + name
                    + " (set it, or run with shoonya.mock=true)");
        }
    }

    private static String normalize(String base) {
        return base.endsWith("/") ? base : base + "/";
    }
}
