/*
 * otpauth:// URI parser — reads the QR payload format used by
 * Google Authenticator and most other TOTP apps, e.g.:
 *
 *   otpauth://totp/Issuer:alice@example.com?secret=JBSWY3DPEHPK3PXP&issuer=Issuer
 *
 * Pure J2ME / CLDC 1.1 — no external dependencies.
 */
package com.twofa.qr;

import com.twofa.crypto.Base32;

import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;

/**
 * Parses otpauth://totp/... URIs from scanned QR codes into
 * an account name + Base32 secret ready for the add-account form.
 */
public final class OtpauthParser {

    /** Parsed result: display name + validated secret. */
    public static final class OtpAuth {
        private final String name;
        private final String secret;

        OtpAuth(String name, String secret) {
            this.name = name;
            this.secret = secret;
        }

        /** Display name, e.g. "Google:alice@gmail.com". */
        public String getName() {
            return name;
        }

        /** Base32 secret (uppercased, whitespace stripped). */
        public String getSecret() {
            return secret;
        }
    }

    private OtpauthParser() {
        // static utility class
    }

    /**
     * Parse an otpauth:// URI.
     *
     * @param text  raw QR payload
     * @return      account name + secret
     * @throws IllegalArgumentException with a user-readable message if the
     *         payload is not a supported TOTP setup URI
     */
    public static OtpAuth parse(String text) {
        if (text == null) {
            throw new IllegalArgumentException("Empty QR code.");
        }
        String s = text.trim();
        if (s.length() == 0) {
            throw new IllegalArgumentException("Empty QR code.");
        }
        if (!startsWithIgnoreCase(s, "otpauth://")) {
            throw new IllegalArgumentException(
                "Not a 2FA setup code (expected otpauth://...).");
        }

        String rest = s.substring("otpauth://".length());

        // --- code type: otpauth://TYPE/... ---
        String type;
        String afterType;
        int slash = rest.indexOf('/');
        int q = rest.indexOf('?');
        if (slash < 0) {
            // No label at all, e.g. "otpauth://totp?secret=ABC"
            type = (q < 0) ? rest : rest.substring(0, q);
            afterType = (q < 0) ? "" : rest.substring(q);
        } else {
            type = rest.substring(0, slash);
            afterType = rest.substring(slash + 1);
        }
        if (type.equalsIgnoreCase("hotp")) {
            throw new IllegalArgumentException(
                "HOTP (counter-based) codes are not supported — use TOTP.");
        }
        if (!type.equalsIgnoreCase("totp")) {
            throw new IllegalArgumentException(
                "Unsupported code type '" + type + "' (only TOTP).");
        }

        // --- label: [issuer:]account (split RAW, then URL-decode parts) ---
        String rawLabel;
        String query;
        int qq = afterType.indexOf('?');
        if (qq < 0) {
            rawLabel = afterType;
            query = "";
        } else {
            rawLabel = afterType.substring(0, qq);
            query = afterType.substring(qq + 1);
        }

        String labelIssuer = "";
        String labelAccount = "";
        int colon = rawLabel.indexOf(':');
        if (colon < 0) {
            labelAccount = urlDecode(rawLabel.trim());
        } else {
            labelIssuer = urlDecode(rawLabel.substring(0, colon).trim());
            labelAccount = urlDecode(rawLabel.substring(colon + 1).trim());
        }

        // --- query parameters (keys case-insensitive) ---
        String secret = null;
        String issuerParam = "";
        String digits = null;
        String period = null;
        String algorithm = null;

        int pos = 0;
        while (pos <= query.length()) {
            int amp = query.indexOf('&', pos);
            if (amp < 0) {
                amp = query.length();
            }
            String pair = query.substring(pos, amp);
            pos = amp + 1;
            if (pair.length() == 0) {
                continue;
            }
            String key;
            String value;
            int eq = pair.indexOf('=');
            if (eq < 0) {
                key = pair;
                value = "";
            } else {
                key = pair.substring(0, eq);
                value = pair.substring(eq + 1);
            }
            key = key.trim().toLowerCase();
            value = urlDecode(value.trim());
            if (key.equals("secret")) {
                secret = value;
            } else if (key.equals("issuer")) {
                issuerParam = value;
            } else if (key.equals("digits")) {
                digits = value;
            } else if (key.equals("period")) {
                period = value;
            } else if (key.equals("algorithm")) {
                algorithm = value;
            }
            // Unknown parameters are ignored.
            if (pos >= query.length()) {
                break;
            }
        }

        if (secret == null || secret.length() == 0) {
            throw new IllegalArgumentException("QR code has no secret key.");
        }

        // This app generates fixed 6-digit / 30-second SHA1 codes.
        // Refuse anything else rather than generate wrong codes.
        if (digits != null && !digits.equals("6")) {
            throw new IllegalArgumentException(
                "Only 6-digit codes are supported (code asks for "
                + digits + ").");
        }
        if (period != null && !period.equals("30")) {
            throw new IllegalArgumentException(
                "Only 30-second codes are supported (code asks for "
                + period + "s).");
        }
        if (algorithm != null && !algorithm.equalsIgnoreCase("SHA1")) {
            throw new IllegalArgumentException(
                "Only SHA1 codes are supported (code asks for "
                + algorithm + ").");
        }

        // Validate the secret is real Base32.
        try {
            byte[] decoded = Base32.decode(secret);
            if (decoded.length == 0) {
                throw new IllegalArgumentException(
                    "QR code secret is empty.");
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                "QR code secret is not valid Base32.");
        }

        // --- display name, like other authenticator apps ---
        String issuer = (issuerParam.length() > 0) ? issuerParam : labelIssuer;
        String account = labelAccount;
        String name;
        if (account.length() == 0) {
            if (issuer.length() == 0) {
                throw new IllegalArgumentException(
                    "QR code has no account name.");
            }
            name = issuer;
        } else if (issuer.length() == 0) {
            name = account;
        } else if (startsWithIgnoreCase(account, issuer + ":")
                || account.equalsIgnoreCase(issuer)) {
            name = account;
        } else {
            name = issuer + ":" + account;
        }

        return new OtpAuth(name, stripSpaces(secret).toUpperCase());
    }

    /**
     * URL-decode %XX sequences and '+' (UTF-8).
     */
    public static String urlDecode(String s) {
        if (s == null) {
            return "";
        }
        if (s.indexOf('%') < 0 && s.indexOf('+') < 0) {
            return s;
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        int i = 0;
        int len = s.length();
        while (i < len) {
            char c = s.charAt(i);
            if (c == '%' && i + 2 < len) {
                int hi = hexVal(s.charAt(i + 1));
                int lo = hexVal(s.charAt(i + 2));
                if (hi >= 0 && lo >= 0) {
                    bos.write((hi << 4) | lo);
                    i += 3;
                    continue;
                }
                // Malformed '%' — keep it literally.
                bos.write((byte) '%');
                i++;
            } else if (c == '+') {
                bos.write((byte) ' ');
                i++;
            } else if (c < 0x80) {
                bos.write((byte) c);
                i++;
            } else {
                // Non-ASCII literal char — encode as UTF-8 bytes.
                if (c < 0x800) {
                    bos.write((byte) (0xC0 | (c >> 6)));
                    bos.write((byte) (0x80 | (c & 0x3F)));
                } else {
                    bos.write((byte) (0xE0 | (c >> 12)));
                    bos.write((byte) (0x80 | ((c >> 6) & 0x3F)));
                    bos.write((byte) (0x80 | (c & 0x3F)));
                }
                i++;
            }
        }
        byte[] bytes = bos.toByteArray();
        try {
            return new String(bytes, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return new String(bytes);
        }
    }

    private static boolean startsWithIgnoreCase(String s, String prefix) {
        if (s.length() < prefix.length()) {
            return false;
        }
        return s.substring(0, prefix.length()).equalsIgnoreCase(prefix);
    }

    private static int hexVal(char c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        if (c >= 'a' && c <= 'f') {
            return c - 'a' + 10;
        }
        if (c >= 'A' && c <= 'F') {
            return c - 'A' + 10;
        }
        return -1;
    }

    private static String stripSpaces(String s) {
        StringBuffer sb = new StringBuffer(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != ' ' && c != '\t' && c != '\n' && c != '\r' && c != '-') {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
