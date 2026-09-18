/*
 * QR decoding from image files (PNG/JPEG bytes).
 * Shared by the camera scanner and the saved-image picker.
 *
 * Large photos (e.g. multi-megapixel camera files) are subsampled
 * row-by-row so decoding never needs a full-size pixel buffer —
 * a 2048x1536 photo decodes from a ~512x384 buffer instead of 12MB.
 *
 * Pure J2ME / CLDC 1.1 — no external dependencies.
 */
package com.twofa.qr;

import javax.microedition.lcdui.Image;

import dk.onlinecity.qrr.QrReader;
import dk.onlinecity.qrr.QrReaderException;

/**
 * Decodes QR codes from encoded image bytes.
 * All failures surface as null / IllegalArgumentException with
 * user-readable messages — never NPEs or raw decoder exceptions.
 */
public final class QrImageDecoder {

    /**
     * Photos up to this many pixels decode at full resolution
     * (640x480 = 307200, the camera snapshot size).
     */
    private static final int MAX_FULL_PIXELS = 640 * 480;

    /** Smallest sampled image still worth trying (a QR needs ~60px). */
    private static final int MIN_SAMPLED_SIZE = 48;

    private QrImageDecoder() {
        // static utility class
    }

    /**
     * Decode a QR code from PNG/JPEG bytes.
     *
     * @param imageBytes  encoded image, or null
     * @return            QR payload text, or null if no QR code found
     */
    public static String decodeQr(byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length == 0) {
            return null;
        }
        Image img;
        try {
            img = Image.createImage(imageBytes, 0, imageBytes.length);
        } catch (Exception e) {
            return null; // not a readable image
        } catch (OutOfMemoryError oom) {
            return null; // image far too big to even open
        }

        int w = img.getWidth();
        int h = img.getHeight();
        int step = computeStep(w, h);
        int sw = (w + step - 1) / step;
        int sh = (h + step - 1) / step;
        if (sw < MIN_SAMPLED_SIZE || sh < MIN_SAMPLED_SIZE) {
            return null;
        }

        int[] pixels;
        try {
            if (step == 1) {
                pixels = new int[w * h];
                img.getRGB(pixels, 0, w, 0, 0, w, h);
            } else {
                pixels = new int[sw * sh];
                int[] row = new int[w];
                int out = 0;
                for (int y = 0; y < h; y += step) {
                    img.getRGB(row, 0, w, 0, y, w, 1);
                    for (int x = 0; x < w; x += step) {
                        pixels[out++] = row[x];
                    }
                }
                row = null;
            }
        } catch (OutOfMemoryError oom) {
            return null;
        } catch (Exception e) {
            return null;
        } finally {
            img = null;
        }

        try {
            QrReader reader = new QrReader(pixels, sw, sh, 8, 6);
            pixels = null;
            String result = null;
            while (result == null) {
                result = reader.scan();
            }
            return result;
        } catch (QrReaderException e) {
            // Thresholds exhausted — no QR code in this image.
            return null;
        } catch (Exception e) {
            // Defensive: malformed candidate geometry, bad indexes, etc.
            return null;
        } catch (OutOfMemoryError oom) {
            return null;
        }
    }

    /**
     * Decode a 2FA setup QR from image bytes.
     *
     * @param imageBytes  encoded image
     * @return            account name + secret
     * @throws IllegalArgumentException with a user-readable message when
     *         the image holds no (supported) 2FA code
     */
    public static OtpauthParser.OtpAuth decodeOtpAuth(byte[] imageBytes) {
        String text = decodeQr(imageBytes);
        if (text == null) {
            throw new IllegalArgumentException("No QR code found in this image.");
        }
        return OtpauthParser.parse(text);
    }

    /**
     * Subsampling step so the sampled image fits MAX_FULL_PIXELS.
     * Package-visible for unit testing.
     */
    static int computeStep(int w, int h) {
        int step = 1;
        while (((long) (w / step)) * (h / step) > MAX_FULL_PIXELS) {
            step++;
        }
        return step;
    }
}
