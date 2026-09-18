/*
 * Account List Screen — main screen of the 2FA app.
 * Shows all stored TOTP accounts. Select one to view code.
 * Options menu: Add, Settings, Delete, Exit.
 */
package com.twofa.ui;

import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.List;
import javax.microedition.lcdui.Alert;
import javax.microedition.lcdui.AlertType;

import java.util.Vector;

import com.twofa.store.Account;
import com.twofa.store.AccountStore;

/**
 * Main list screen. Displays all saved TOTP accounts.
 */
public final class AccountListScreen extends List implements CommandListener {

    private final Display display;
    private final TwoFACallback callback;
    private Vector accounts;

    // Commands
    private final Command cmdSelect;
    private final Command cmdAdd;
    private final Command cmdScan;
    private final Command cmdDelete;
    private final Command cmdSettings;
    private final Command cmdExit;

    /**
     * Callback interface to communicate with the MIDlet.
     */
    public interface TwoFACallback {
        void showDetail(Account acct, int index);
        void showAddAccount();
        void showScan();
        void showSettings();
        void exitApp();
    }

    public AccountListScreen(Display display, TwoFACallback callback) {
        super("2FA", List.IMPLICIT);
        this.display = display;
        this.callback = callback;

        // Commands — Nokia E71 has Left soft key (SELECT) and Right soft key (BACK/OPTIONS)
        cmdSelect = new Command("Open", Command.OK, 1);
        cmdAdd = new Command("Add", Command.SCREEN, 2);
        cmdScan = new Command("Scan", Command.SCREEN, 3);
        cmdDelete = new Command("Delete", Command.SCREEN, 4);
        cmdSettings = new Command("Settings", Command.SCREEN, 5);
        cmdExit = new Command("Exit", Command.EXIT, 10);

        addCommand(cmdSelect);
        addCommand(cmdAdd);
        addCommand(cmdScan);
        addCommand(cmdDelete);
        addCommand(cmdSettings);
        addCommand(cmdExit);

        setSelectCommand(cmdSelect);
        setCommandListener(this);

        loadAccounts();
    }

    /**
     * (Re)load accounts from RMS and refresh the list.
     */
    public void loadAccounts() {
        accounts = AccountStore.loadAll();
        rebuildList();
    }

    private void rebuildList() {
        deleteAll();
        if (accounts.size() == 0) {
            append("(no accounts — press Add)", null);
        } else {
            for (int i = 0; i < accounts.size(); i++) {
                Account acct = (Account) accounts.elementAt(i);
                append(acct.getName(), null);
            }
        }
    }

    public void commandAction(Command c, Displayable d) {
        if (c == cmdSelect) {
            int idx = getSelectedIndex();
            if (accounts.size() > 0 && idx >= 0 && idx < accounts.size()) {
                callback.showDetail((Account) accounts.elementAt(idx), idx);
            }
        } else if (c == cmdAdd) {
            callback.showAddAccount();
        } else if (c == cmdScan) {
            callback.showScan();
        } else if (c == cmdDelete) {
            int idx = getSelectedIndex();
            if (accounts.size() > 0 && idx >= 0 && idx < accounts.size()) {
                deleteAccount(idx);
            }
        } else if (c == cmdSettings) {
            callback.showSettings();
        } else if (c == cmdExit) {
            callback.exitApp();
        }
    }

    private void deleteAccount(int idx) {
        Account acct = (Account) accounts.elementAt(idx);
        AccountStore.deleteAccount(idx);
        loadAccounts();
        Alert done = new Alert("Deleted",
            acct.getName() + " removed.",
            null, AlertType.CONFIRMATION);
        done.setTimeout(1500);
        display.setCurrent(done, this);
    }
}
