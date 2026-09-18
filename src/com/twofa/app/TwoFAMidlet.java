/*
 * 2FA — TOTP Authenticator MIDlet for Nokia E71
 * Main entry point. Wires up all screens.
 *
 * Platform: MIDP 2.0 / CLDC 1.1
 * No external dependencies — pure J2ME.
 */
package com.twofa.app;

import javax.microedition.midlet.MIDlet;
import javax.microedition.lcdui.Display;

import com.twofa.ui.AccountListScreen;
import com.twofa.ui.AccountDetailScreen;
import com.twofa.ui.AddAccountScreen;
import com.twofa.ui.ImagePickerScreen;
import com.twofa.ui.ScanScreen;
import com.twofa.ui.SettingsScreen;
import com.twofa.store.Account;
import com.twofa.store.AccountStore;

import javax.microedition.lcdui.Alert;
import javax.microedition.lcdui.AlertType;

/**
 * Main MIDlet. Implements the callback interface so the
 * list screen can request screen transitions.
 */
public final class TwoFAMidlet extends MIDlet
        implements AccountListScreen.TwoFACallback, ScanScreen.ScanCallback,
                   ImagePickerScreen.ImagePickerCallback {

    private Display display;
    private AccountListScreen listScreen;

    // Keep references so they survive GC
    private AddAccountScreen addScreen;
    private SettingsScreen settingsScreen;

    public TwoFAMidlet() {
    }

    protected void startApp() {
        display = Display.getDisplay(this);
        listScreen = new AccountListScreen(display, this);
        display.setCurrent(listScreen);
    }

    protected void pauseApp() {
        // Nothing special needed
    }

    protected void destroyApp(boolean unconditional) {
        // Clean exit
    }

    // --- TwoFACallback implementation ---

    public void showDetail(Account acct, int index) {
        // Wrap the back-navigation in a Runnable
        Runnable back = new Runnable() {
            public void run() {
                listScreen.loadAccounts();
                display.setCurrent(listScreen);
            }
        };
        AccountDetailScreen detail =
            new AccountDetailScreen(display, acct, index, back);
        display.setCurrent(detail);
    }

    public void showAddAccount() {
        if (addScreen == null) {
            addScreen = new AddAccountScreen(display, listScreen,
                new Runnable() {
                    public void run() {
                        showScan();
                    }
                },
                new Runnable() {
                    public void run() {
                        showImagePicker();
                    }
                });
        }
        display.setCurrent(addScreen);
    }

    public void showScan() {
        try {
            ScanScreen scan = new ScanScreen(display, this);
            display.setCurrent(scan);
        } catch (Throwable t) {
            // Phone without a camera API (JSR-135): fall back to typing.
            Alert info = new Alert("No Camera",
                "QR scanning needs a phone with a camera API. "
                + "Please enter the details manually.",
                null, AlertType.INFO);
            info.setTimeout(2500);
            showAddAccount();
            display.setCurrent(info, addScreen);
        }
    }

    /**
     * Open the add form with name + secret filled in from a QR scan,
     * for the user to review and save — like other authenticator apps.
     */
    public void showAddAccountPrefilled(String name, String secret) {
        showAddAccount();
        addScreen.setPrefill(name, secret);
    }

    // --- ScanScreen.ScanCallback implementation ---

    public void scanSucceeded(String name, String secret) {
        showAddAccountPrefilled(name, secret);
    }

    public void scanCancelled() {
        listScreen.loadAccounts();
        display.setCurrent(listScreen);
    }

    // --- ImagePickerScreen.ImagePickerCallback implementation ---

    public void showImagePicker() {
        try {
            ImagePickerScreen picker = new ImagePickerScreen(display, this);
            display.setCurrent(picker);
        } catch (Throwable t) {
            // Phone without file access (JSR-75): fall back to typing.
            Alert info = new Alert("No Files",
                "Loading images needs a phone with file access. "
                + "Please enter the details manually.",
                null, AlertType.INFO);
            info.setTimeout(2500);
            showAddAccount();
            display.setCurrent(info, addScreen);
        }
    }

    public void imagePickSucceeded(String name, String secret) {
        showAddAccountPrefilled(name, secret);
    }

    public void imagePickCancelled() {
        listScreen.loadAccounts();
        display.setCurrent(listScreen);
    }

    public void showSettings() {
        // Recreate each time so it picks up current offset value
        settingsScreen = new SettingsScreen(display, listScreen);
        display.setCurrent(settingsScreen);
    }

    public void exitApp() {
        try {
            destroyApp(true);
        } catch (Exception e) {
            // ignore
        }
        notifyDestroyed();
    }
}
