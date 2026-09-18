/*
 * Account data model for 2FA app.
 */
package com.twofa.store;

/**
 * Represents a single TOTP account entry.
 * Lightweight — all fields are simple types for CLDC 1.1.
 */
public final class Account {

    private String name;          // display name (e.g. "Google:alice@gmail.com")
    private String base32Secret;  // raw Base32-encoded secret key
    private int clockOffset;      // manual clock offset in seconds

    public Account() {
        this("", "", 0);
    }

    public Account(String name, String base32Secret, int clockOffset) {
        this.name = name;
        this.base32Secret = base32Secret;
        this.clockOffset = clockOffset;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getBase32Secret() {
        return base32Secret;
    }

    public void setBase32Secret(String base32Secret) {
        this.base32Secret = base32Secret;
    }

    public int getClockOffset() {
        return clockOffset;
    }

    public void setClockOffset(int clockOffset) {
        this.clockOffset = clockOffset;
    }

    /**
     * Serialize to a string for RMS storage.
     * Format: name\0base32Secret\0clockOffset
     * Using null char as delimiter since it won't appear in valid inputs.
     */
    public String serialize() {
        return name + "\0" + base32Secret + "\0" + String.valueOf(clockOffset);
    }

    /**
     * Deserialize from RMS string.
     */
    public static Account deserialize(String data) {
        int i1 = data.indexOf('\0');
        int i2 = data.indexOf('\0', i1 + 1);

        if (i1 < 0 || i2 < 0) {
            return null;
        }

        String name = data.substring(0, i1);
        String secret = data.substring(i1 + 1, i2);
        int offset = 0;
        try {
            offset = Integer.parseInt(data.substring(i2 + 1));
        } catch (Exception e) {
            // default to 0
        }

        return new Account(name, secret, offset);
    }

    public String toString() {
        return name;
    }
}
