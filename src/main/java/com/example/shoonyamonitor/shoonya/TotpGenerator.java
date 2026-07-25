package com.example.shoonyamonitor.shoonya;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;

/**
 * RFC 6238 TOTP (time-based one-time password) generator.
 *
 * <p>Shoonya's {@code factor2} is a 6-digit TOTP. If the user supplies the
 * shared secret (the Base32 string behind the authenticator QR code) this
 * class produces the current code so logins can be fully automated.</p>
 */
public final class TotpGenerator {

    private static final int DIGITS = 6;
    private static final int PERIOD_SECONDS = 30;

    private TotpGenerator() {
    }

    /** Current 6-digit code for the given Base32 secret. */
    public static String now(String base32Secret) {
        return at(base32Secret, System.currentTimeMillis() / 1000L);
    }

    static String at(String base32Secret, long epochSeconds) {
        byte[] key = base32Decode(base32Secret);
        long counter = epochSeconds / PERIOD_SECONDS;
        byte[] msg = ByteBuffer.allocate(8).putLong(counter).array();
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(msg);
            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);
            int otp = binary % (int) Math.pow(10, DIGITS);
            return String.format("%0" + DIGITS + "d", otp);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to generate TOTP", e);
        }
    }

    private static byte[] base32Decode(String s) {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
        String clean = s.trim().replace(" ", "").replace("-", "").toUpperCase();
        int buffer = 0;
        int bitsLeft = 0;
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        for (char c : clean.toCharArray()) {
            if (c == '=') {
                break;
            }
            int val = alphabet.indexOf(c);
            if (val < 0) {
                continue; // skip anything that is not valid Base32
            }
            buffer = (buffer << 5) | val;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                bitsLeft -= 8;
                out.write((buffer >> bitsLeft) & 0xFF);
            }
        }
        return out.toByteArray();
    }
}
