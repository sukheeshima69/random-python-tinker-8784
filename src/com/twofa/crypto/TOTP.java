/*
 * TOTP (RFC 6238) implementation for J2ME / CLDC 1.1
 * Uses HMAC-SHA1 — produces standard 6-digit codes.
 */
package com.twofa.crypto;

/**
 * Time-based One-Time Password generator.
 * Standard 30-second time step, 6-digit code, HMAC-SHA1.
 */
public final class TOTP {

    /** Default time step in seconds. */
    public static final int TIME_STEP = 30;

    /** Number of digits in the generated code. */
    public static final int DIGITS = 6;

    /**
     * Generate a 6-digit TOTP code.
     *
     * @param secret      raw secret key bytes (after Base32 decode)
     * @param unixTimeMs  current time in milliseconds since epoch
     * @param offsetSec   manual clock offset in seconds (+ = phone is fast)
     * @return            6-digit code as a zero-padded String, e.g. "048291"
     */
    public static String generate(byte[] secret, long unixTimeMs, int offsetSec) {
        // Apply manual offset (positive = phone clock is ahead, so subtract)
        long adjustedMs = unixTimeMs - ((long) offsetSec * 1000L);

        // Counter = floor(adjustedTime / step)
        long counter = adjustedMs / (TIME_STEP * 1000L);

        // Encode counter as 8-byte big-endian
        byte[] counterBytes = new byte[8];
        counterBytes[0] = (byte) (counter >>> 56);
        counterBytes[1] = (byte) (counter >>> 48);
        counterBytes[2] = (byte) (counter >>> 40);
        counterBytes[3] = (byte) (counter >>> 32);
        counterBytes[4] = (byte) (counter >>> 24);
        counterBytes[5] = (byte) (counter >>> 16);
        counterBytes[6] = (byte) (counter >>> 8);
        counterBytes[7] = (byte) (counter);

        // HMAC-SHA1
        byte[] hmac = HMACSHA1.hmac(secret, counterBytes);

        // Dynamic truncation (RFC 4226 / RFC 6238)
        int offset = hmac[19] & 0x0F;
        int binary = ((hmac[offset]     & 0x7F) << 24)
                   | ((hmac[offset + 1] & 0xFF) << 16)
                   | ((hmac[offset + 2] & 0xFF) << 8)
                   |  (hmac[offset + 3] & 0xFF);

        int otp = binary % 1000000;  // 6 digits

        // Zero-pad to 6 digits
        String code = String.valueOf(otp);
        while (code.length() < 6) {
            code = "0" + code;
        }
        return code;
    }

    /**
     * Get seconds remaining in the current TOTP period.
     *
     * @param unixTimeMs  current time in milliseconds since epoch
     * @param offsetSec   manual clock offset in seconds
     * @return            seconds remaining (30..1)
     */
    public static int remainingSeconds(long unixTimeMs, int offsetSec) {
        long adjustedMs = unixTimeMs - ((long) offsetSec * 1000L);
        long periodMs = TIME_STEP * 1000L;
        long intoPeriod = adjustedMs % periodMs;
        int remaining = (int) (TIME_STEP - (intoPeriod / 1000L));
        if (remaining <= 0) remaining = TIME_STEP;
        return remaining;
    }

    /**
     * Generate with system clock (convenience).
     */
    public static String generate(byte[] secret, int offsetSec) {
        return generate(secret, System.currentTimeMillis(), offsetSec);
    }

    /**
     * Remaining seconds with system clock (convenience).
     */
    public static int remainingSeconds(int offsetSec) {
        return remainingSeconds(System.currentTimeMillis(), offsetSec);
    }
}
