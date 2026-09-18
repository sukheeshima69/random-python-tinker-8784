/*
 * Image Picker Screen — load a saved QR-code photo (PNG/JPEG) from the
 * phone or memory card and decode the 2FA setup code inside it.
 * Useful for QR codes received by Bluetooth, MMS or download.
 *
 * Uses the FileConnection API (JSR-75). The phone asks for file-read
 * permission on first use. File loading + decoding run on a worker
 * thread; the UI thread is never blocked.
 *
 * Platform: MIDP 2.0 / CLDC 1.1 + JSR-75 (standard on the Nokia E71).
 */
package com.twofa.ui;

import javax.microedition.io.Connector;
import javax.microedition.io.file.FileConnection;
import javax.microedition.io.file.FileSystemRegistry;

import javax.microedition.lcdui.Alert;
import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.List;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import java.util.Enumeration;
import java.util.Vector;

import com.twofa.qr.QrImageDecoder;
import com.twofa.qr.OtpauthParser.OtpAuth;

/**
 * Minimal image-file browser. On picking a file the QR inside is decoded
 * and the MIDlet is told the account name + secret for auto-fill.
 */
public final class ImagePickerScreen extends List
        implements CommandListener, Runnable {

    /** Callback into the MIDlet. */
    public interface ImagePickerCallback {
        void imagePickSucceeded(String name, String secret);
        void imagePickCancelled();
    }

    /** Refuse files bigger than this (a phone photo is far smaller). */
    private static final long MAX_FILE_BYTES = 2L * 1024L * 1024L;

    private final Display display;
    private final ImagePickerCallback callback;

    private final Command cmdOpen;
    private final Command cmdBack;

    /** Current directory URL, or null for the drives view. */
    private String currentUrl;
    /** Parent directory URLs when navigating ("" = drives view). */
    private final Vector navStack;
    /** Per-row target URL (directory or file). Empty = message row. */
    private final Vector entryUrls;
    /** Per-row Boolean: true = directory. */
    private final Vector entryIsDir;

    private Thread worker;
    private String pendingFileUrl;
    private boolean loading;

    public ImagePickerScreen(Display display, ImagePickerCallback callback) {
        super("QR Image", List.IMPLICIT);
        this.display = display;
        this.callback = callback;

        navStack = new Vector();
        entryUrls = new Vector();
        entryIsDir = new Vector();

        cmdOpen = new Command("Open", Command.OK, 1);
        cmdBack = new Command("Back", Command.BACK, 2);

        addCommand(cmdOpen);
        addCommand(cmdBack);
        setCommandListener(this);
    }

    protected void showNotify() {
        if (!loading) {
            refresh();
        }
    }

    public void commandAction(Command c, Displayable d) {
        if (c == cmdBack) {
            goBack();
        } else {
            // cmdOpen or implicit select
            openSelected();
        }
    }

    // --- navigation ---

    private void goBack() {
        if (loading) {
            return;
        }
        if (currentUrl == null) {
            callback.imagePickCancelled();
            return;
        }
        if (navStack.isEmpty()) {
            currentUrl = null;
        } else {
            String parent = (String) navStack.lastElement();
            navStack.removeElementAt(navStack.size() - 1);
            currentUrl = (parent.length() == 0) ? null : parent;
        }
        refresh();
    }

    private void openSelected() {
        if (loading) {
            return;
        }
        int idx = getSelectedIndex();
        if (idx < 0 || idx >= entryUrls.size()) {
            return;
        }
        String url = (String) entryUrls.elementAt(idx);
        boolean isDir = ((Boolean) entryIsDir.elementAt(idx)).booleanValue();
        if (isDir) {
            navStack.addElement((currentUrl == null) ? "" : currentUrl);
            currentUrl = url;
            refresh();
        } else {
            startLoad(url);
        }
    }

    private void refresh() {
        try {
            doRefresh();
        } catch (NoClassDefFoundError ncd) {
            showStaticMessage("(file access not supported on this phone)");
        } catch (SecurityException se) {
            showStaticMessage("(allow file access, then reopen)");
        } catch (Throwable t) {
            showStaticMessage("(cannot list files here)");
        }
    }

    private void showStaticMessage(String msg) {
        deleteAll();
        entryUrls.removeAllElements();
        entryIsDir.removeAllElements();
        append(msg, null);
    }

    private void doRefresh() throws IOException {
        deleteAll();
        entryUrls.removeAllElements();
        entryIsDir.removeAllElements();

        if (currentUrl == null) {
            setTitle("QR Image");
            Enumeration roots = FileSystemRegistry.listRoots();
            boolean any = false;
            while (roots.hasMoreElements()) {
                String root = (String) roots.nextElement();
                append(friendlyRootName(root), null);
                entryUrls.addElement("file:///" + root);
                entryIsDir.addElement(Boolean.TRUE);
                any = true;
            }
            if (!any) {
                append("(no drives found)", null);
            }
            return;
        }

        setTitle(shortName(currentUrl));
        FileConnection dir = null;
        Vector dirs = new Vector();
        Vector files = new Vector();
        try {
            dir = (FileConnection) Connector.open(currentUrl, Connector.READ);
            Enumeration e = dir.list();
            while (e.hasMoreElements()) {
                String name = (String) e.nextElement();
                if (name.endsWith("/")) {
                    dirs.addElement(name);
                } else if (isImageFile(name)) {
                    files.addElement(name);
                }
            }
        } finally {
            closeConnection(dir);
        }

        for (int i = 0; i < dirs.size(); i++) {
            String name = (String) dirs.elementAt(i);
            append(name, null);
            entryUrls.addElement(currentUrl + name);
            entryIsDir.addElement(Boolean.TRUE);
        }
        for (int i = 0; i < files.size(); i++) {
            String name = (String) files.elementAt(i);
            append(name, null);
            entryUrls.addElement(currentUrl + name);
            entryIsDir.addElement(Boolean.FALSE);
        }
        if (dirs.size() == 0 && files.size() == 0) {
            append("(no images here)", null);
        }
    }

    // --- loading + decoding (worker thread) ---

    private void startLoad(String fileUrl) {
        if (loading) {
            return;
        }
        loading = true;
        pendingFileUrl = fileUrl;
        Alert wait = new Alert("Loading",
            "Reading image, please wait...",
            null, AlertType.INFO);
        wait.setTimeout(Alert.FOREVER);
        display.setCurrent(wait);
        worker = new Thread(this);
        worker.start();
    }

    public void run() {
        String url = pendingFileUrl;
        String failMessage = "Cannot read this image.";
        try {
            byte[] data = readFile(url);
            final OtpAuth auth;
            try {
                auth = QrImageDecoder.decodeOtpAuth(data);
            } catch (IllegalArgumentException e) {
                failMessage = e.getMessage();
                throw e;
            }
            final String name = auth.getName();
            final String secret = auth.getSecret();
            display.callSerially(new Runnable() {
                public void run() {
                    callback.imagePickSucceeded(name, secret);
                }
            });
            return;
        } catch (OutOfMemoryError oom) {
            failMessage = "Image is too large for this phone.";
            System.gc();
        } catch (SecurityException se) {
            failMessage = "File access blocked — allow it and retry.";
        } catch (IOException ioe) {
            String m = ioe.getMessage();
            if (m != null && m.length() > 0 && m.length() < 60) {
                failMessage = m;
            }
        } catch (IllegalArgumentException e) {
            // failMessage already set above
        } catch (Throwable t) {
            // keep default message
        } finally {
            loading = false;
            worker = null;
        }
        final String msg = failMessage;
        display.callSerially(new Runnable() {
            public void run() {
                Alert err = new Alert("No Code", msg, null, AlertType.ERROR);
                err.setTimeout(2500);
                display.setCurrent(err, ImagePickerScreen.this);
            }
        });
    }

    /**
     * Read a whole file into memory, enforcing the size guard first.
     */
    private static byte[] readFile(String url) throws IOException {
        FileConnection fc = null;
        InputStream in = null;
        try {
            fc = (FileConnection) Connector.open(url, Connector.READ);
            long size = fc.fileSize();
            if (size > MAX_FILE_BYTES) {
                throw new IOException("Image is too large (over 2MB).");
            }
            in = fc.openInputStream();
            ByteArrayOutputStream bos = new ByteArrayOutputStream(
                size > 0 ? (int) Math.min(size, 65536) : 4096);
            byte[] buf = new byte[4096];
            int n;
            long total = 0;
            while ((n = in.read(buf)) != -1) {
                total += n;
                if (total > MAX_FILE_BYTES) {
                    throw new IOException("Image is too large (over 2MB).");
                }
                bos.write(buf, 0, n);
            }
            return bos.toByteArray();
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    // ignore
                }
            }
            closeConnection(fc);
        }
    }

    private static void closeConnection(FileConnection fc) {
        if (fc != null) {
            try {
                fc.close();
            } catch (IOException e) {
                // ignore
            }
        }
    }

    // --- helpers ---

    private static boolean isImageFile(String name) {
        String n = name.toLowerCase();
        return n.endsWith(".png") || n.endsWith(".jpg")
            || n.endsWith(".jpeg");
    }

    private static String friendlyRootName(String root) {
        if (root.startsWith("E") || root.startsWith("e")) {
            return "Memory card (" + root + ")";
        }
        if (root.startsWith("C") || root.startsWith("c")) {
            return "Phone memory (" + root + ")";
        }
        return "Drive (" + root + ")";
    }

    /** Last path segment, without trailing '/'. */
    private static String shortName(String url) {
        String s = url;
        if (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        int i = s.lastIndexOf('/');
        if (i >= 0) {
            s = s.substring(i + 1);
        }
        if (s.length() == 0) {
            return "QR Image";
        }
        if (s.length() > 20) {
            s = s.substring(0, 20) + "...";
        }
        return s;
    }
}
