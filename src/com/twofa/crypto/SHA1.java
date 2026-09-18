/*
 * SHA-1 implementation for J2ME / CLDC 1.1
 * Pure Java — no javax.crypto, no java.security needed.
 * Based on RFC 3174 / FIPS 180-4.
 *
 * This is intentionally kept minimal for .jar size on Nokia E71.
 */
package com.twofa.crypto;

/**
 * Standalone SHA-1 hash. Feed it bytes, get a 20-byte digest.
 */
public final class SHA1 {

    private static final int[] K = {
        0x5A827999, // 0..19
        0x6ED9EBA1, // 20..39
        0x8F1BBCDC, // 40..59
        0xCA62C1D6  // 60..79
    };

    // Working state
    private int h0, h1, h2, h3, h4;
    private long messageLength;   // total bits processed
    private final int[] w = new int[80];
    private final byte[] buffer = new byte[64];
    private int bufferOffset;

    public SHA1() {
        reset();
    }

    /** Reset to initial state so the instance can be reused. */
    public void reset() {
        h0 = 0x67452301;
        h1 = 0xEFCDAB89;
        h2 = 0x98BADCFE;
        h3 = 0x10325476;
        h4 = 0xC3D2E1F0;
        messageLength = 0;
        bufferOffset = 0;
    }

    /**
     * Feed arbitrary-length data. Call as many times as needed, then finish().
     */
    public void update(byte[] data, int offset, int length) {
        messageLength += (long) length << 3;  // track bits

        while (length > 0) {
            int space = 64 - bufferOffset;
            int copy = (length < space) ? length : space;
            System.arraycopy(data, offset, buffer, bufferOffset, copy);
            bufferOffset += copy;
            offset += copy;
            length -= copy;
            if (bufferOffset == 64) {
                processBlock();
                bufferOffset = 0;
            }
        }
    }

    public void update(byte[] data) {
        update(data, 0, data.length);
    }

    /**
     * Pad, process final block(s), and return the 20-byte digest.
     * Resets internal state — call reset() before reuse if needed.
     */
    public byte[] finish() {
        // Append 0x80
        buffer[bufferOffset++] = (byte) 0x80;

        // If not enough room for the 8-byte length, pad & process this block
        if (bufferOffset > 56) {
            while (bufferOffset < 64) {
                buffer[bufferOffset++] = 0;
            }
            processBlock();
            bufferOffset = 0;
        }

        // Pad to 56 bytes
        while (bufferOffset < 56) {
            buffer[bufferOffset++] = 0;
        }

        // Append message length in bits as big-endian 64-bit
        buffer[56] = (byte) (messageLength >>> 56);
        buffer[57] = (byte) (messageLength >>> 48);
        buffer[58] = (byte) (messageLength >>> 40);
        buffer[59] = (byte) (messageLength >>> 32);
        buffer[60] = (byte) (messageLength >>> 24);
        buffer[61] = (byte) (messageLength >>> 16);
        buffer[62] = (byte) (messageLength >>> 8);
        buffer[63] = (byte) (messageLength);
        processBlock();

        byte[] digest = new byte[20];
        intToBytes(h0, digest, 0);
        intToBytes(h1, digest, 4);
        intToBytes(h2, digest, 8);
        intToBytes(h3, digest, 12);
        intToBytes(h4, digest, 16);

        reset();
        return digest;
    }

    /**
     * Convenience: hash an entire byte array in one call.
     */
    public static byte[] hash(byte[] data) {
        SHA1 s = new SHA1();
        s.update(data);
        return s.finish();
    }

    // --- internals ---

    private void processBlock() {
        // Prepare message schedule
        for (int i = 0; i < 16; i++) {
            w[i] = ((buffer[i * 4] & 0xFF) << 24)
                 | ((buffer[i * 4 + 1] & 0xFF) << 16)
                 | ((buffer[i * 4 + 2] & 0xFF) << 8)
                 |  (buffer[i * 4 + 3] & 0xFF);
        }
        for (int i = 16; i < 80; i++) {
            w[i] = rotl32(w[i - 3] ^ w[i - 8] ^ w[i - 14] ^ w[i - 16], 1);
        }

        int a = h0, b = h1, c = h2, d = h3, e = h4;

        for (int t = 0; t < 80; t++) {
            int temp = rotl32(a, 5) + f(t, b, c, d) + e + K[t / 20] + w[t];
            e = d;
            d = c;
            c = rotl32(b, 30);
            b = a;
            a = temp;
        }

        h0 += a;
        h1 += b;
        h2 += c;
        h3 += d;
        h4 += e;
    }

    private static int f(int t, int b, int c, int d) {
        if (t < 20)  return (b & c) | ((~b) & d);
        if (t < 40)  return b ^ c ^ d;
        if (t < 60)  return (b & c) | (b & d) | (c & d);
        return b ^ c ^ d;
    }

    private static int rotl32(int val, int bits) {
        return (val << bits) | (val >>> (32 - bits));
    }

    private static void intToBytes(int val, byte[] out, int off) {
        out[off]     = (byte) (val >>> 24);
        out[off + 1] = (byte) (val >>> 16);
        out[off + 2] = (byte) (val >>> 8);
        out[off + 3] = (byte) (val);
    }
}
