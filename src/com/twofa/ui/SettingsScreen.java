/*
 * Settings Screen — clock offset configuration.
 * Critical for E71: phones may not have NTP-synced clocks,
 * so users need to manually adjust the time offset.
 */
package com.twofa.ui;

import javax.microedition.lcdui.Alert;
import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.TextField;
import javax.microedition.lcdui.StringItem;

import com.twofa.store.AccountStore;

/**
 * Settings form — allows adjusting the global clock offset.
 * Positive offset = phone clock is ahead of real time.
 * Negative offset = phone clock is behind real time.
 */
public final class SettingsScreen extends Form implements CommandListener {

    private final Display display;
    private final Displayable previousScreen;

    private final TextField tfOffset;
    private final Command cmdSave;
    private final Command cmdBack;

    public SettingsScreen(Display display, Displayable previousScreen) {
        super("Settings");
        this.display = display;
        this.previousScreen = previousScreen;

        int currentOffset = AccountStore.loadClockOffset();

        // Explanation
        StringItem help = new StringItem(null,
            "Clock Offset (seconds)\n\n"
            + "If your codes don't match, your phone's clock "
            + "may be off. Compare with a known-good clock.\n\n"
            + "Positive = phone is fast\n"
            + "Negative = phone is slow\n\n"
            + "Current phone time: " + getPhoneTimeStr() + "\n");
        append(help);

        tfOffset = new TextField("Offset (sec)", String.valueOf(currentOffset),
                                  8, TextField.NUMERIC);
        append(tfOffset);

        cmdSave = new Command("Save", Command.OK, 1);
        cmdBack = new Command("Back", Command.BACK, 2);
        addCommand(cmdSave);
        addCommand(cmdBack);
        setCommandListener(this);
    }

    public void commandAction(Command c, Displayable d) {
        if (c == cmdSave) {
            doSave();
        } else if (c == cmdBack) {
            display.setCurrent(previousScreen);
        }
    }

    private void doSave() {
        String val = tfOffset.getString().trim();
        int offset = 0;
        try {
            offset = Integer.parseInt(val);
        } catch (Exception e) {
            Alert err = new Alert("Error", "Enter a valid number.",
                                  null, AlertType.ERROR);
            err.setTimeout(1500);
            display.setCurrent(err, this);
            return;
        }

        AccountStore.saveClockOffset(offset);

        Alert done = new Alert("Saved",
            "Clock offset set to " + offset + " seconds.",
            null, AlertType.CONFIRMATION);
        done.setTimeout(1500);
        display.setCurrent(done, previousScreen);
    }

    private String getPhoneTimeStr() {
        long ms = System.currentTimeMillis();
        long totalSec = ms / 1000;
        long sec = totalSec % 60;
        long min = (totalSec / 60) % 60;
        long hr = (totalSec / 3600) % 24;
        // Simple HH:MM:SS (UTC) — we can't do timezone on CLDC 1.1 easily
        return pad2(hr) + ":" + pad2(min) + ":" + pad2(sec) + " UTC";
    }

    private String pad2(long v) {
        return (v < 10) ? "0" + v : String.valueOf(v);
    }
}
