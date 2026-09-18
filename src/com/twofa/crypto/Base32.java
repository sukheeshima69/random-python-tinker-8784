/*
 * Base32 decoder for J2ME / CLDC 1.1
 * Handles standard Base32 (RFC 4648) used by Google Authenticator,
 * Authy, and most TOTP providers.
 */
package com.twofa.crypto;

/**
 * Base32 decoding. Handles uppercase/lowercase and strips whitespace/dashes.
 */
public final class Base32 {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    /**
     * Decode a Base32-encoded string into raw bytes.
     *
     * @param encoded  Base32 string (may contain spaces, dashes, lowercase)
     * @return         decoded bytes
     * @throws IllegalArgumentException if the input contains invalid characters
     */
    public static byte[] decode(String encoded) {
        // Normalize: uppercase, strip whitespace and padding
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < encoded.length(); i++) {
            char c = encoded.charAt(i);
            if (c == '=' || c == ' ' || c == '-' || c == '\n' || c == '\r' || c == '\t') {
                continue;
            }
            if (c >= 'a' && c <= 'z') {
                c = (char) (c - 32); // to uppercase
            }
            sb.append(c);
        }
        String clean = sb.toString();

        int len = clean.length();
        int outputLen = (len * 5) / 8;
        byte[] output = new byte[outputLen];

        int buffer = 0;
        int bitsInBuffer = 0;
        int outputIndex = 0;

        for (int i = 0; i < len; i++) {
            char c = clean.charAt(i);
            int val = ALPHABET.indexOf(c);
            if (val < 0) {
                throw new IllegalArgumentException("Invalid Base32 char: " + c);
            }

            buffer = (buffer << 5) | val;
            bitsInBuffer += 5;

            if (bitsInBuffer >= 8) {
                bitsInBuffer -= 8;
                if (outputIndex < outputLen) {
                    output[outputIndex++] = (byte) (buffer >>> bitsInBuffer);
                }
                buffer &= (1 << bitsInBuffer) - 1;  // mask remaining bits
            }
        }

        return output;
    }

    /**
     * Encode bytes to Base32 string (useful for debugging/testing).
     */
    public static String encode(byte[] data) {
        StringBuffer sb = new StringBuffer();
        int buffer = 0;
        int bitsInBuffer = 0;

        for (int i = 0; i < data.length; i++) {
            buffer = (buffer << 8) | (data[i] & 0xFF);
            bitsInBuffer += 8;

            while (bitsInBuffer >= 5) {
                bitsInBuffer -= 5;
                sb.append(ALPHABET.charAt((buffer >>> bitsInBuffer) & 0x1F));
                buffer &= (1 << bitsInBuffer) - 1;
            }
        }

        // Remaining bits
        if (bitsInBuffer > 0) {
            sb.append(ALPHABET.charAt((buffer << (5 - bitsInBuffer)) & 0x1F));
        }

        return sb.toString();
    }
}
