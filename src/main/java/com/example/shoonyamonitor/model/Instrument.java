package com.example.shoonyamonitor.model;

/**
 * A subscribable instrument. {@code key} is the Shoonya feed key "EXCH|TOKEN".
 *
 * @param exchange    e.g. NSE / BSE
 * @param token       numeric scrip token as a string
 * @param displayName human friendly label used in the UI
 * @param index       true if this is an index (rendered at the top, not ranked)
 */
public record Instrument(String exchange, String token, String displayName, boolean index) {

    /** Feed subscription / lookup key, e.g. {@code NSE|26000}. */
    public String key() {
        return exchange + "|" + token;
    }

    /**
     * Parses a config entry of the form {@code EXCH|TOKEN|DISPLAY NAME}.
     * The display name is optional and defaults to the token.
     */
    public static Instrument parse(String raw, boolean index) {
        String trimmed = raw.trim();
        String[] parts = trimmed.split("\\|", 3);
        if (parts.length < 2) {
            throw new IllegalArgumentException("Invalid instrument definition: '" + raw
                    + "'. Expected EXCH|TOKEN|NAME");
        }
        String exchange = parts[0].trim();
        String token = parts[1].trim();
        String name = parts.length == 3 && !parts[2].isBlank() ? parts[2].trim() : token;
        return new Instrument(exchange, token, name, index);
    }
}
