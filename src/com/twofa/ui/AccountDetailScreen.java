/*
 * Account Detail Screen — shows the current TOTP code with countdown.
 * Uses a custom Canvas to draw the 6-digit code large and a countdown ring.
 * Refreshes every second via a Timer.
 */
package com.twofa.ui;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;

import com.twofa.crypto.Base32;
import com.twofa.crypto.TOTP;
import com.twofa.store.Account;
import com.twofa.store.AccountStore;

import java.util.Timer;
import java.util.TimerTask;

/**
 * Full-screen display of a single account's TOTP code.
 * Auto-refreshes every second.
 */
public final class AccountDetailScreen extends Canvas implements CommandListener {

    private final Display display;
    private final Runnable backCallback;
    private final Account account;
    private final byte[] secretBytes;

    private final Command cmdBack;

    private Timer refreshTimer;
    private String currentCode = "------";
    private int remaining = 0;
    private String lastError = null;

    // Colors — warm amber/cream/teal palette
    private static final int BG_COLOR       = 0x1A1A2E;
    private static final int TEXT_COLOR      = 0xFFD166;
    private static final int SUBTEXT_COLOR   = 0xA0A0B0;
    private static final int RING_COLOR      = 0x06D6A0;
    private static final int RING_BG_COLOR   = 0x2A2A3E;
    private static final int WARN_COLOR      = 0xEF476F;

    public AccountDetailScreen(Display display, Account account, int index,
                                Runnable backCallback) {
        this.display = display;
        this.account = account;
        this.backCallback = backCallback;

        byte[] decoded = null;
        try {
            decoded = Base32.decode(account.getBase32Secret());
        } catch (Exception e) {
            lastError = "Bad secret: " + e.getMessage();
            decoded = new byte[0];
        }
        this.secretBytes = decoded;

        cmdBack = new Command("Back", Command.BACK, 1);
        addCommand(cmdBack);
        setCommandListener(this);

        regenerateCode();
    }

    protected void showNotify() {
        super.showNotify();
        refreshTimer = new Timer();
        refreshTimer.schedule(new TimerTask() {
            public void run() {
                regenerateCode();
                repaint();
            }
        }, 0, 1000);
    }

    protected void hideNotify() {
        super.hideNotify();
        if (refreshTimer != null) {
            refreshTimer.cancel();
            refreshTimer = null;
        }
    }

    private void regenerateCode() {
        if (secretBytes != null && secretBytes.length > 0) {
            try {
                int offset = AccountStore.loadClockOffset();
                currentCode = TOTP.generate(secretBytes, offset);
                remaining = TOTP.remainingSeconds(offset);
            } catch (Exception e) {
                currentCode = "ERROR";
                lastError = e.getMessage();
            }
        }
    }

    protected void paint(Graphics g) {
        int w = getWidth();
        int h = getHeight();

        g.setColor(BG_COLOR);
        g.fillRect(0, 0, w, h);

        // Account name
        Font nameFont = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_BOLD, Font.SIZE_SMALL);
        g.setFont(nameFont);
        g.setColor(SUBTEXT_COLOR);
        g.drawString(account.getName(), w / 2, 8, Graphics.TOP | Graphics.HCENTER);

        // Countdown ring
        int ringCx = w / 2;
        int ringCy = h / 3;
        int ringRadius = Math.min(w, h) / 5;
        if (ringRadius < 20) ringRadius = 20;

        // Ring track
        g.setColor(RING_BG_COLOR);
        g.drawArc(ringCx - ringRadius, ringCy - ringRadius,
                   ringRadius * 2, ringRadius * 2, 0, 360);

        // Active arc
        int arcAngle = (int) (((long) remaining * 360L) / TOTP.TIME_STEP);
        int ringColor = (remaining <= 5) ? WARN_COLOR : RING_COLOR;
        g.setColor(ringColor);
        g.drawArc(ringCx - ringRadius, ringCy - ringRadius,
                   ringRadius * 2, ringRadius * 2, 90, -arcAngle);

        // Seconds inside ring
        Font secFont = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        g.setFont(secFont);
        g.setColor(ringColor);
        g.drawString(String.valueOf(remaining) + "s",
                     ringCx, ringCy, Graphics.TOP | Graphics.HCENTER);

        // Big 6-digit code
        Font codeFont = Font.getFont(Font.FACE_MONOSPACE, Font.STYLE_BOLD, Font.SIZE_LARGE);
        g.setFont(codeFont);
        g.setColor(TEXT_COLOR);
        String displayCode;
        if (currentCode.length() == 6) {
            displayCode = currentCode.substring(0, 3) + " " + currentCode.substring(3);
        } else {
            displayCode = currentCode;
        }
        int codeY = ringCy + ringRadius + 16;
        g.drawString(displayCode, w / 2, codeY, Graphics.TOP | Graphics.HCENTER);

        // Progress bar
        int barW = w - 40;
        int barX = 20;
        int barY = codeY + codeFont.getHeight() + 8;
        g.setColor(RING_BG_COLOR);
        g.fillRect(barX, barY, barW, 4);
        int fillW = (barW * remaining) / TOTP.TIME_STEP;
        g.setColor(ringColor);
        g.fillRect(barX, barY, fillW, 4);

        // Error
        if (lastError != null) {
            Font errFont = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_PLAIN, Font.SIZE_SMALL);
            g.setFont(errFont);
            g.setColor(WARN_COLOR);
            g.drawString(lastError, w / 2, h - 30, Graphics.TOP | Graphics.HCENTER);
        }

        // Footer
        Font hintFont = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        g.setFont(hintFont);
        g.setColor(SUBTEXT_COLOR);
        g.drawString("Code refreshes every 30s", w / 2, h - 14,
                     Graphics.TOP | Graphics.HCENTER);
    }

    public void commandAction(Command c, Displayable d) {
        if (c == cmdBack) {
            if (refreshTimer != null) {
                refreshTimer.cancel();
                refreshTimer = null;
            }
            backCallback.run();
        }
    }
}
