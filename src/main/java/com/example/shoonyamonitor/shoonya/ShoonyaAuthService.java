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

    public ShoonyaAuthService(ShoonyaProperties props) {
        this.props = props;
    }

    /**
     * Resolves the session token used to open the feed.
     *
     * <p>If an OAuth access token is configured it is returned directly.
     * Otherwise this falls back to the deprecated QuickAuth login.</p>
     *
     * @throws IllegalStateException if no token is available or login fails
     */
    public String login() {
        require(props.getUserId(), "shoonya.user-id");

        String accessToken = props.getAccessToken();
        if (accessToken != null && !accessToken.isBlank()) {
            log.info("Using OAuth access token for uid {} (account {})",
                    props.getUserId(), props.getAccountId());
            return accessToken.trim();
        }

        log.warn("No shoonya.access-token set - falling back to the DEPRECATED "
                + "QuickAuth login. Its endpoint is retired and will likely fail "
                + "with HTTP 502. Set SHOONYA_ACCESS_TOKEN to use the OAuth feed.");
        return quickAuthLogin();
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
