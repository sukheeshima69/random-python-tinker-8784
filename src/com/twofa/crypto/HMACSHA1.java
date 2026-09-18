/*
 * HMAC-SHA1 for J2ME / CLDC 1.1
 * RFC 2104 compliant, using our hand-rolled SHA1.
 */
package com.twofa.crypto;

/**
 * HMAC-SHA1 keyed hash. Zero external dependencies.
 */
public final class HMACSHA1 {

    private final SHA1 sha;
    private final byte[] ipad;
    private final byte[] opad;

    /**
     * @param key  secret key bytes (any length)
     */
    public HMACSHA1(byte[] key) {
        sha = new SHA1();

        // If key > 64 bytes, hash it down
        if (key.length > 64) {
            key = SHA1.hash(key);
        }

        byte[] k = new byte[64];
        System.arraycopy(key, 0, k, 0, key.length);
        // rest is already zero-filled

        ipad = new byte[64];
        opad = new byte[64];

        for (int i = 0; i < 64; i++) {
            ipad[i] = (byte) (k[i] ^ 0x36);
            opad[i] = (byte) (k[i] ^ 0x5C);
        }
    }

    /**
     * Compute HMAC-SHA1 over the given data.
     *
     * @param data  message bytes
     * @return      20-byte HMAC
     */
    public byte[] doFinal(byte[] data) {
        // Inner hash: H(ipad || data)
        sha.reset();
        sha.update(ipad);
        sha.update(data);
        byte[] inner = sha.finish();

        // Outer hash: H(opad || inner)
        sha.reset();
        sha.update(opad);
        sha.update(inner);
        return sha.finish();
    }

    /**
     * Convenience static method.
     */
    public static byte[] hmac(byte[] key, byte[] data) {
        return new HMACSHA1(key).doFinal(data);
    }
}
