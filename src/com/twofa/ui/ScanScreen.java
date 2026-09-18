/*
 * Scan Screen — camera QR-code scanner for TOTP enrolment.
 * Shows a live viewfinder (MMAPI direct video), captures a snapshot
 * on demand, decodes the QR on-device and hands the otpauth:// payload
 * to the MIDlet for auto-fill.
 *
 * Platform: MIDP 2.0 / CLDC 1.1 + MMAPI 1.1 (JSR-135, "capture://video").
 * Camera work runs on a worker thread; the UI thread is never blocked.
 */
package com.twofa.ui;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;
import javax.microedition.media.Manager;
import javax.microedition.media.Player;
import javax.microedition.media.control.VideoControl;

import com.twofa.qr.QrImageDecoder;
import com.twofa.qr.OtpauthParser;
import com.twofa.qr.OtpauthParser.OtpAuth;

/**
 * Full-screen camera scanner. On a successful scan the MIDlet is told
 * the account name + secret so the add-account form can be auto-filled.
 */
public final class ScanScreen extends Canvas implements CommandListener, Runnable {

    /** Callback into the MIDlet. */
    public interface ScanCallback {
        void scanSucceeded(String name, String secret);
        void scanCancelled();
    }

    private static final int TASK_INIT = 1;
    private static final int TASK_CAPTURE = 2;

    /**
     * Snapshot encodings to try, largest first. The default (null) on
     * some phones is a multi-megapixel photo that exhausts the heap,
     * so an explicit 640x480 JPEG is tried first.
     */
    private static final String[] SNAPSHOT_TYPES = {
        "encoding=jpeg&width=640&height=480",
        null,
        "encoding=jpeg&width=320&height=240",
    };

    private static final int HEADER_H = 22;
    private static final int FOOTER_H = 22;

    private static final int BG_COLOR     = 0x1A1A2E;
    private static final int TEXT_COLOR   = 0xFFD166;
    private static final int SUBTEXT_COLOR = 0xA0A0B0;
    private static final int WARN_COLOR   = 0xEF476F;

    private final Display display;
    private final ScanCallback callback;

    private final Command cmdCapture;
    private final Command cmdBack;

    private Player player;
    private VideoControl video;

    private Thread worker;
    private int task;
    private boolean busy;
    private boolean closed;
    private boolean ready;

    private String status = "Starting camera...";
    private String error;

    public ScanScreen(Display display, ScanCallback callback) {
        this.display = display;
        this.callback = callback;

        cmdCapture = new Command("Capture", Command.OK, 1);
        cmdBack = new Command("Back", Command.BACK, 2);

        addCommand(cmdCapture);
        addCommand(cmdBack);
        setCommandListener(this);
    }

    protected void showNotify() {
        closed = false;
        if (player == null) {
            launch(TASK_INIT);
        }
    }

    protected void hideNotify() {
        closed = true;
        closePlayer();
    }

    public void commandAction(Command c, Displayable d) {
        if (c == cmdCapture) {
            error = null;
            launch(TASK_CAPTURE);
        } else if (c == cmdBack) {
            closed = true;
            closePlayer();
            callback.scanCancelled();
        }
    }

    protected void keyPressed(int keyCode) {
        int action;
        try {
            action = getGameAction(keyCode);
        } catch (Exception e) {
            return; // not a game key — ignore
        }
        if (action == FIRE) {
            error = null;
            launch(TASK_CAPTURE);
        }
    }

    private synchronized void launch(int newTask) {
        if (busy || closed) {
            return;
        }
        busy = true;
        task = newTask;
        worker = new Thread(this);
        worker.start();
    }

    public void run() {
        try {
            if (task == TASK_INIT) {
                doInit();
            } else {
                doCapture();
            }
        } finally {
            synchronized (this) {
                busy = false;
                worker = null;
            }
            repaint();
        }
    }

    // --- camera setup (worker thread) ---

    private void doInit() {
        try {
            player = Manager.createPlayer("capture://video");
            player.realize();
            video = (VideoControl) player.getControl("VideoControl");
            if (video == null) {
                fail("No viewfinder on this phone.");
                closePlayer();
                return;
            }
            video.initDisplayMode(VideoControl.USE_DIRECT_VIDEO, this);
            layoutVideo();
            try {
                video.setVisible(true);
            } catch (Exception e) {
                // Some phones show the viewfinder without this call.
            }
            player.start();
            ready = true;
            status = "Aim at the QR code, press Capture.";
        } catch (Throwable t) {
            fail(cameraErrorMessage(t));
            closePlayer();
        }
    }

    private void layoutVideo() {
        int vw = getWidth();
        int vh = getHeight() - HEADER_H - FOOTER_H;
        if (vh < 10) {
            vh = 10;
        }
        try {
            video.setDisplayLocation(0, HEADER_H);
        } catch (Exception e) {
            // keep default location
        }
        try {
            video.setDisplaySize(vw, vh);
        } catch (Throwable t) {
            // keep default size
        }
    }

    // --- capture + decode (worker thread) ---

    private void doCapture() {
        if (!ready || video == null) {
            fail("Camera not ready yet — wait a moment.");
            return;
        }
        status = "Capturing...";
        repaint();

        String text = null;
        String lastProblem = "no photo";
        for (int i = 0; i < SNAPSHOT_TYPES.length && text == null; i++) {
            if (closed) {
                return;
            }
            byte[] snap = null;
            try {
                snap = video.getSnapshot(SNAPSHOT_TYPES[i]);
            } catch (SecurityException se) {
                fail("Camera blocked — allow access and retry.");
                return;
            } catch (Throwable t) {
                lastProblem = shortMessage(t, "snapshot failed");
                continue;
            }
            if (snap == null || snap.length == 0) {
                lastProblem = "empty photo";
                continue;
            }
            try {
                text = decodeSnapshot(snap);
                if (text == null) {
                    lastProblem = "code not recognised";
                }
            } catch (OutOfMemoryError oom) {
                // Photo too big for the heap — GC and try a smaller one.
                lastProblem = "photo too big";
                System.gc();
            } catch (Throwable t) {
                lastProblem = shortMessage(t, "unreadable photo");
            } finally {
                snap = null;
                System.gc();
            }
        }

        if (closed) {
            return;
        }
        if (text == null) {
            fail("No QR code found (" + lastProblem + "). "
               + "Fill the frame, hold steady, retry.");
            return;
        }

        final OtpAuth auth;
        try {
            auth = OtpauthParser.parse(text);
        } catch (IllegalArgumentException e) {
            fail(e.getMessage());
            return;
        }

        final String name = auth.getName();
        final String secret = auth.getSecret();
        closePlayer();
        display.callSerially(new Runnable() {
            public void run() {
                callback.scanSucceeded(name, secret);
            }
        });
    }

    /**
     * Decode a JPEG/PNG snapshot into a String, or null if no QR found.
     */
    private String decodeSnapshot(byte[] snap) {
        try {
            return QrImageDecoder.decodeQr(snap);
        } catch (Throwable t) {
            return null;
        }
    }

    // --- helpers ---

    private void fail(String msg) {
        error = msg;
        status = "Press Capture to retry, or Back.";
    }

    private void setStatus(String msg) {
        status = msg;
        repaint();
    }

    private void closePlayer() {
        ready = false;
        if (video != null) {
            try {
                video.setVisible(false);
            } catch (Exception e) {
                // ignore
            }
            video = null;
        }
        if (player != null) {
            try {
                player.close();
            } catch (Exception e) {
                // ignore
            }
            player = null;
        }
    }

    private static String cameraErrorMessage(Throwable t) {
        if (t instanceof SecurityException) {
            return "Camera blocked — allow access and retry.";
        }
        if (t instanceof NoClassDefFoundError) {
            return "No camera API on this phone.";
        }
        String m = t.getMessage();
        if (t instanceof IllegalStateException
                || (m != null && m.indexOf("ealize") >= 0)) {
            return "Camera is busy — close other apps, retry.";
        }
        if (m != null && m.length() > 0 && m.length() < 60) {
            return "Camera error: " + m;
        }
        return "Cannot open the camera on this phone.";
    }

    private static String shortMessage(Throwable t, String fallback) {
        String m = t.getMessage();
        if (m != null && m.length() > 0 && m.length() < 40) {
            return m;
        }
        return fallback;
    }

    // --- painting ---

    protected void paint(Graphics g) {
        int w = getWidth();
        int h = getHeight();

        g.setColor(BG_COLOR);
        g.fillRect(0, 0, w, h);

        g.setColor(TEXT_COLOR);
        g.setFont(Font.getFont(Font.FACE_PROPORTIONAL,
                               Font.STYLE_BOLD, Font.SIZE_MEDIUM));
        g.drawString("Scan QR Code", w >> 1, 3,
                     Graphics.TOP | Graphics.HCENTER);

        g.setFont(Font.getFont(Font.FACE_PROPORTIONAL,
                               Font.STYLE_PLAIN, Font.SIZE_SMALL));
        if (error != null) {
            g.setColor(WARN_COLOR);
            g.drawString(fit(g, error, w - 8), w >> 1, h - FOOTER_H + 4,
                         Graphics.TOP | Graphics.HCENTER);
        } else {
            g.setColor(SUBTEXT_COLOR);
            g.drawString(fit(g, status, w - 8), w >> 1, h - FOOTER_H + 4,
                         Graphics.TOP | Graphics.HCENTER);
        }
    }

    /** Truncate a line with "..." so it fits the footer. */
    private static String fit(Graphics g, String s, int maxWidth) {
        if (s == null) {
            return "";
        }
        if (g.getFont().stringWidth(s) <= maxWidth) {
            return s;
        }
        while (s.length() > 4
                && g.getFont().stringWidth(s + "...") > maxWidth) {
            s = s.substring(0, s.length() - 1);
        }
        return s + "...";
    }
}
