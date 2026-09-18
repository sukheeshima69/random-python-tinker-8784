/*
 * Add Account Screen — manual entry form for TOTP account.
 * Fields: Account Name, Base32 Secret Key.
 * Validates Base32 input before saving.
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

import com.twofa.crypto.Base32;
import com.twofa.store.Account;
import com.twofa.store.AccountStore;

/**
 * Form screen for manually adding a new TOTP account.
 * User enters name and Base32-encoded secret.
 */
public final class AddAccountScreen extends Form implements CommandListener {

    private final Display display;
    private final Displayable previousScreen;

    private final TextField tfName;
    private final TextField tfSecret;

    private final Command cmdSave;
    private final Command cmdScan;
    private final Command cmdImage;
    private final Command cmdCancel;

    private final Runnable scanAction;
    private final Runnable loadAction;

    public AddAccountScreen(Display display, Displayable previousScreen) {
        this(display, previousScreen, null, null);
    }

    /**
     * @param scanAction  invoked for the "Scan QR" command, or null to hide it
     * @param loadAction  invoked for the "Load image" command, or null to hide it
     */
    public AddAccountScreen(Display display, Displayable previousScreen,
                            Runnable scanAction) {
        this(display, previousScreen, scanAction, null);
    }

    public AddAccountScreen(Display display, Displayable previousScreen,
                            Runnable scanAction, Runnable loadAction) {
        super("Add Account");
        this.display = display;
        this.previousScreen = previousScreen;
        this.scanAction = scanAction;
        this.loadAction = loadAction;

        tfName = new TextField("Account Name", "", 64, TextField.ANY);
        tfSecret = new TextField("Secret Key (Base32)", "", 128, TextField.ANY);

        append(tfName);
        append(tfSecret);

        // Help text
        String help = "\nEnter the account name (e.g. \"Google:user@gmail.com\") "
             + "and the Base32 secret key provided by the service.\n\n"
             + "The secret is usually shown when you set up 2FA. "
             + "It looks like: JBSWY3DPEHPK3PXP";
        if (scanAction != null) {
            help += "\n\nTip: press Scan QR or Load image to fill this "
                  + "in from the service's QR code.";
        }
        append(help);

        cmdSave = new Command("Save", Command.OK, 1);
        cmdScan = new Command("Scan QR", Command.SCREEN, 2);
        cmdImage = new Command("Load image", Command.SCREEN, 3);
        cmdCancel = new Command("Cancel", Command.CANCEL, 4);

        addCommand(cmdSave);
        if (scanAction != null) {
            addCommand(cmdScan);
        }
        if (loadAction != null) {
            addCommand(cmdImage);
        }
        addCommand(cmdCancel);
        setCommandListener(this);
    }

    /**
     * Pre-fill the form, e.g. after a successful QR scan.
     * The user reviews the details and presses Save.
     */
    public void setPrefill(String name, String secret) {
        tfName.setString(name != null ? name : "");
        tfSecret.setString(secret != null ? secret : "");
    }

    public void commandAction(Command c, Displayable d) {
        if (c == cmdSave) {
            doSave();
        } else if (c == cmdScan) {
            if (scanAction != null) {
                scanAction.run();
            }
        } else if (c == cmdImage) {
            if (loadAction != null) {
                loadAction.run();
            }
        } else if (c == cmdCancel) {
            display.setCurrent(previousScreen);
        }
    }

    private void doSave() {
        String name = tfName.getString().trim();
        String secret = tfSecret.getString().trim();

        // Validate name
        if (name.length() == 0) {
            showError("Please enter an account name.");
            return;
        }

        // Validate secret
        if (secret.length() == 0) {
            showError("Please enter the Base32 secret key.");
            return;
        }

        // Try decoding the Base32 secret to validate it
        try {
            byte[] decoded = Base32.decode(secret);
            if (decoded.length == 0) {
                showError("Secret key decodes to empty. Check the value.");
                return;
            }
        } catch (IllegalArgumentException e) {
            showError("Invalid Base32: " + e.getMessage());
            return;
        }

        // Save to RMS
        Account acct = new Account(name, secret.toUpperCase(), 0);
        AccountStore.addAccount(acct);

        // Clear fields
        tfName.setString("");
        tfSecret.setString("");

        // Show confirmation and go back
        Alert done = new Alert("Saved",
            name + " added successfully!",
            null, AlertType.CONFIRMATION);
        done.setTimeout(1500);
        display.setCurrent(done, previousScreen);
    }

    private void showError(String msg) {
        Alert err = new Alert("Error", msg, null, AlertType.ERROR);
        err.setTimeout(2000);
        display.setCurrent(err, this);
    }
}
